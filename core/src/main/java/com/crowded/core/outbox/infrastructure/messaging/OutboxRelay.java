package com.crowded.core.outbox.infrastructure.messaging;

import com.crowded.core.outbox.domain.model.OutboxMessage;
import com.crowded.core.outbox.domain.repository.OutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes the outbox to RabbitMQ, oldest first, and removes each message once the broker has
 * confirmed it. A message that fails stays in the outbox and is published again, with the same
 * message id, on the next run; consumers skip a message they already processed.
 *
 * <p>Each message is published in the trace of the request that wrote it, with its traceparent
 * header, so the consumer logs under the same trace id. The RabbitTemplate's own observation stays
 * off: it would replace that header with the trace of this scheduled run.
 */
@Component
class OutboxRelay {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

  /** Messages per run; the rest wait for the next one. */
  static final int BATCH_SIZE = 100;

  /** Header with the commit time of the change, from which the consumer measures its latency. */
  static final String CREATED_AT = "created-at";

  private static final String TRACE_PARENT = "traceparent";

  private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(5);

  private final OutboxRepository outbox;
  private final RabbitTemplate rabbit;
  private final Tracer tracer;
  private final Propagator propagator;
  private final Clock clock;
  private final Timer lag;

  /** Commit time of the oldest message the last run left waiting; {@code null} when none. */
  private final AtomicReference<Instant> oldestWaiting = new AtomicReference<>();

  OutboxRelay(
      OutboxRepository outbox,
      RabbitTemplate rabbit,
      Tracer tracer,
      Propagator propagator,
      MeterRegistry meters,
      Clock clock) {
    this.outbox = outbox;
    this.rabbit = rabbit;
    this.tracer = tracer;
    this.propagator = propagator;
    this.clock = clock;
    this.lag =
        Timer.builder("crowded.outbox.lag")
            .description("Time from a change's commit until RabbitMQ confirmed its message")
            .publishPercentileHistogram()
            .register(meters);
    Gauge.builder("crowded.outbox.waiting", oldestWaiting, oldest -> waitingSeconds(oldest, clock))
        .description("How long the oldest message RabbitMQ has not confirmed has waited")
        .baseUnit("seconds")
        .register(meters);
  }

  /** Runs every half second, well inside the 1-minute delivery target. */
  @Scheduled(fixedDelay = 500)
  @Transactional
  public void publishWaiting() {
    for (OutboxMessage message : outbox.lockOldest(BATCH_SIZE)) {
      if (!publish(message)) {
        // Keep the order: the rest waits for the next run.
        oldestWaiting.set(message.getCreatedAt());
        return;
      }
      outbox.remove(message.getId());
      lag.record(Duration.between(message.getCreatedAt(), clock.instant()));
    }
    oldestWaiting.set(null);
  }

  /** Publishes the message in its request's trace, and says whether the broker confirmed it. */
  private boolean publish(OutboxMessage message) {
    Span span =
        propagator
            .extract(
                message, (stored, key) -> TRACE_PARENT.equals(key) ? stored.getTraceParent() : null)
            .name("outbox publish")
            .start();
    try (Tracer.SpanInScope _ = tracer.withSpan(span)) {
      try {
        Message amqp = toAmqp(message, span);
        rabbit.invoke(
            operations -> {
              operations.send(MessagingConfiguration.EXCHANGE, message.getRoutingKey(), amqp);
              operations.waitForConfirmsOrDie(CONFIRM_TIMEOUT.toMillis());
              return null;
            });
        log.info(
            "Published outbox message {} with routing key {}",
            message.getId(),
            message.getRoutingKey());
        return true;
      } catch (AmqpException e) {
        span.error(e);
        log.warn("Outbox message {} not published, retrying later", message.getId(), e);
        return false;
      }
    } finally {
      span.end();
    }
  }

  private Message toAmqp(OutboxMessage message, Span span) {
    MessageProperties properties = new MessageProperties();
    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    properties.setMessageId(message.getId().toString());
    properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
    properties.setHeader(CREATED_AT, message.getCreatedAt().toString());
    propagator.inject(span.context(), properties, MessageProperties::setHeader);
    return MessageBuilder.withBody(message.getPayload().getBytes(StandardCharsets.UTF_8))
        .andProperties(properties)
        .build();
  }

  private static double waitingSeconds(AtomicReference<Instant> oldest, Clock clock) {
    Instant waitingSince = oldest.get();
    return waitingSince == null
        ? 0
        : Duration.between(waitingSince, clock.instant()).toMillis() / 1000.0;
  }
}
