package com.crowded.core.ticket.application.service;

import com.crowded.core.ticket.application.dto.ExtractionOutcome;
import com.crowded.core.ticket.application.dto.ExtractionResult;
import com.crowded.core.ticket.domain.model.TicketExtraction;
import com.crowded.core.ticket.domain.repository.TicketExtractor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Asks the AI provider once, with a timeout and without retries, and keeps its answer only when it
 * is one JSON object that follows the extraction schema. It never throws: when the provider is
 * down, too slow or answers anything else, the result has no event code and the participant types
 * it. Nothing from the ticket or the answer is logged, only the outcome and the duration.
 */
@Service
class TicketExtractionRunner implements AutoCloseable {

  static final String TIMER = "crowded.tickets.extraction";

  private static final Logger log = LoggerFactory.getLogger(TicketExtractionRunner.class);

  private static final JsonMapper STRICT =
      JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
  private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() {};

  private final TicketExtractor extractor;
  private final Duration timeout;
  private final MeterRegistry meters;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  TicketExtractionRunner(
      TicketExtractor extractor,
      @Value("${crowded.ai.ticket-extraction-timeout:25s}") Duration timeout,
      MeterRegistry meters) {
    this.extractor = extractor;
    this.timeout = timeout;
    this.meters = meters;
  }

  ExtractionResult extract(String textWithoutPersonalData) {
    long start = System.nanoTime();
    ExtractionResult result = run(textWithoutPersonalData);
    long elapsed = System.nanoTime() - start;
    Timer.builder(TIMER)
        .description("AI ticket extractions, by outcome")
        .tag("outcome", result.outcome().name())
        .register(meters)
        .record(elapsed, TimeUnit.NANOSECONDS);
    log.info(
        "Ticket extraction finished: outcome={}, durationMs={}",
        result.outcome(),
        TimeUnit.NANOSECONDS.toMillis(elapsed));
    return result;
  }

  private ExtractionResult run(String textWithoutPersonalData) {
    Future<String> call = executor.submit(() -> extractor.extract(textWithoutPersonalData));
    String answer;
    try {
      answer = call.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      call.cancel(true);
      return ExtractionResult.failed(ExtractionOutcome.TIMEOUT);
    } catch (ExecutionException e) {
      return ExtractionResult.failed(ExtractionOutcome.UNAVAILABLE);
    } catch (InterruptedException e) {
      call.cancel(true);
      Thread.currentThread().interrupt();
      return ExtractionResult.failed(ExtractionOutcome.UNAVAILABLE);
    }
    return parse(answer)
        .flatMap(TicketExtraction::fromProviderFields)
        .map(ExtractionResult::of)
        .orElseGet(() -> ExtractionResult.failed(ExtractionOutcome.INVALID_OUTPUT));
  }

  /** The answer's fields, or empty when it is not exactly one JSON object. */
  private static Optional<Map<String, Object>> parse(String answer) {
    if (answer == null || answer.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.ofNullable(STRICT.readValue(answer, OBJECT));
    } catch (JacksonException e) {
      return Optional.empty();
    }
  }

  @Override
  public void close() {
    executor.close();
  }
}
