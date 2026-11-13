package com.crowded.core.ticket.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.crowded.core.ticket.application.dto.ExtractionOutcome;
import com.crowded.core.ticket.application.dto.ExtractionResult;
import com.crowded.core.ticket.domain.model.TicketDetails;
import com.crowded.core.ticket.domain.model.TicketExtraction;
import com.crowded.core.ticket.domain.repository.TicketExtractor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TicketExtractionRunnerTest {

  private static final TicketExtractor UNAVAILABLE =
      text -> {
        throw new IllegalStateException("No AI provider is configured");
      };

  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

  private TicketExtractionRunner runner(TicketExtractor extractor, Duration timeout) {
    return new TicketExtractionRunner(extractor, timeout, meters);
  }

  private ExtractionResult extract(TicketExtractor extractor) {
    try (TicketExtractionRunner runner = runner(extractor, Duration.ofSeconds(5))) {
      return runner.extract("Código do evento: MARG-2026");
    }
  }

  @Test
  void returnsWhatTheAiFound() {
    ExtractionResult result = extract(text -> "{\"eventCode\": \"MARG-2026\", \"gate\": \"A\"}");

    assertThat(result.outcome()).isEqualTo(ExtractionOutcome.EXTRACTED);
    assertThat(result.extraction())
        .isEqualTo(new TicketExtraction("MARG-2026", new TicketDetails("A", null, null)));
  }

  @Test
  void sendsOnlyTheTextItIsGiven() {
    StringBuilder received = new StringBuilder();

    extract(
        text -> {
          received.append(text);
          return "{}";
        });

    assertThat(received).hasToString("Código do evento: MARG-2026");
  }

  @Test
  void reportsAnAnswerWithoutAnEventCode() {
    ExtractionResult result = extract(text -> "{\"seat\": \"14\"}");

    assertThat(result.outcome()).isEqualTo(ExtractionOutcome.NO_EVENT_CODE);
    assertThat(result.extraction().extractedFields().seat()).isEqualTo("14");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "not json",
        "null",
        "[]",
        "{\"eventCode\": \"MARG-2026\", \"action\": \"delete\"}",
        "{\"eventCode\": {\"value\": \"MARG-2026\"}}",
        "{\"eventCode\": \"MARG-2026\"} {\"eventCode\": \"OTHER\"}",
        "Here is the result: {\"eventCode\": \"MARG-2026\"}"
      })
  void discardsAnAnswerThatIsNotOneObjectOfTheSchema(String answer) {
    ExtractionResult result = extract(text -> answer);

    assertThat(result.outcome()).isEqualTo(ExtractionOutcome.INVALID_OUTPUT);
    assertThat(result.extraction()).isEqualTo(TicketExtraction.NOTHING);
  }

  @Test
  void fallsBackWhenTheProviderIsUnavailable() {
    ExtractionResult result = extract(UNAVAILABLE);

    assertThat(result.outcome()).isEqualTo(ExtractionOutcome.UNAVAILABLE);
    assertThat(result.extraction()).isEqualTo(TicketExtraction.NOTHING);
  }

  @Test
  void stopsWaitingAfterTheTimeoutAndCancelsTheCall() throws InterruptedException {
    CountDownLatch interrupted = new CountDownLatch(1);
    TicketExtractor slow =
        text -> {
          try {
            Thread.sleep(Duration.ofSeconds(30));
          } catch (InterruptedException e) {
            interrupted.countDown();
            Thread.currentThread().interrupt();
          }
          return "{\"eventCode\": \"MARG-2026\"}";
        };

    long start = System.nanoTime();
    ExtractionResult result;
    try (TicketExtractionRunner runner = runner(slow, Duration.ofMillis(200))) {
      result = runner.extract("text");
    }

    assertThat(result.outcome()).isEqualTo(ExtractionOutcome.TIMEOUT);
    assertThat(result.extraction()).isEqualTo(TicketExtraction.NOTHING);
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
    assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  void recordsTheDurationByOutcome() {
    extract(text -> "{\"eventCode\": \"MARG-2026\"}");
    extract(UNAVAILABLE);

    assertThat(meters.get(TicketExtractionRunner.TIMER).tag("outcome", "EXTRACTED").timer().count())
        .isEqualTo(1);
    assertThat(
            meters.get(TicketExtractionRunner.TIMER).tag("outcome", "UNAVAILABLE").timer().count())
        .isEqualTo(1);
  }
}
