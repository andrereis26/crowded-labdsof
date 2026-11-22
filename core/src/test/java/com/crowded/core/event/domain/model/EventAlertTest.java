package com.crowded.core.event.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crowded.core.shared.domain.exception.ConflictException;
import com.crowded.core.shared.domain.exception.RuleViolationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The operational alerts an organization sends to the participants connected to its event. */
class EventAlertTest {

  private static final Instant NOW = Instant.parse("2026-11-20T21:00:00Z");
  private static final UUID ORGANIZATION = UUID.randomUUID();

  @Test
  void sendsTheAlertOfTheEventAndItsOrganizationNowWithTheMessageStripped() {
    Event event = event(EventStatus.ONGOING);

    Alert alert = event.sendAlert(AlertType.OTHER, "  Água grátis no bar.\n", 42, NOW);

    assertThat(alert.getEventId()).isEqualTo(event.getId());
    assertThat(alert.getOrganizationId()).isEqualTo(ORGANIZATION);
    assertThat(alert.getType()).isEqualTo(AlertType.OTHER);
    assertThat(alert.getMessage()).isEqualTo("Água grátis no bar.");
    assertThat(alert.getSentAt()).isEqualTo(NOW);
    assertThat(alert.getRecipients()).isEqualTo(42);
    assertThat(alert)
        .isNotEqualTo(event.sendAlert(AlertType.OTHER, "Água grátis no bar.", 42, NOW));
  }

  @ParameterizedTest
  @CsvSource({
    "ENTRANCE_CLOSURE, CRITICAL",
    "CAPACITY_REACHED, WARNING",
    "SCHEDULE_DELAY, WARNING",
    "OTHER, WARNING"
  })
  void itsTypeDecidesHowProminentItIs(AlertType type, Severity severity) {
    assertThat(event(EventStatus.SCHEDULED).sendAlert(type, "Mensagem.", 0, NOW).getSeverity())
        .isEqualTo(severity);
  }

  @Test
  void refusesAnAlertWithoutAType() {
    assertThatThrownBy(() -> event(EventStatus.ONGOING).sendAlert(null, "Mensagem.", 0, NOW))
        .isInstanceOf(RuleViolationException.class)
        .hasFieldOrPropertyWithValue("code", "ALERT_TYPE_REQUIRED");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   ", "\n\t"})
  void refusesAMissingOrBlankMessage(String message) {
    assertThatThrownBy(() -> event(EventStatus.ONGOING).sendAlert(AlertType.OTHER, message, 0, NOW))
        .isInstanceOf(RuleViolationException.class)
        .hasFieldOrPropertyWithValue("code", "ALERT_MESSAGE_REQUIRED");
  }

  @Test
  void acceptsAMessageUpToItsLimitAndRefusesALongerOne() {
    String longest = "a".repeat(AlertMessage.MAX_LENGTH);

    assertThat(new AlertMessage(" " + longest + " ").text()).isEqualTo(longest);
    assertThatThrownBy(() -> new AlertMessage(longest + "a"))
        .isInstanceOf(RuleViolationException.class)
        .hasFieldOrPropertyWithValue("code", "ALERT_MESSAGE_TOO_LONG");
  }

  @ParameterizedTest
  @EnumSource(
      value = EventStatus.class,
      names = {"CANCELLED", "ENDED"})
  void refusesAnAlertAboutAnEventThatIsOver(EventStatus status) {
    assertThatThrownBy(() -> event(status).sendAlert(AlertType.OTHER, "Mensagem.", 0, NOW))
        .isInstanceOf(ConflictException.class)
        .hasFieldOrPropertyWithValue("code", "EVENT_CLOSED");
  }

  @Test
  void neverCountsANegativeNumberOfRecipients() {
    assertThatThrownBy(
            () -> event(EventStatus.ONGOING).sendAlert(AlertType.OTHER, "Mensagem.", -1, NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static Event event(EventStatus status) {
    return StoredEvents.restore(
        UUID.randomUUID(),
        ORGANIZATION,
        new EventState(
            status,
            new Schedule(NOW.minusSeconds(3600), null, NOW.plusSeconds(7200)),
            new Venue("Coliseu", "Porto", null)));
  }
}
