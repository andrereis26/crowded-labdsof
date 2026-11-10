package com.crowded.core.event.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventChangeTest {

  private static final Instant START = Instant.parse("2026-11-20T20:00:00Z");
  private static final EventState SCHEDULED =
      new EventState(EventStatus.SCHEDULED, START, null, null, "Coliseu", "Porto", 41.1, -8.6);
  private static final EventState RELOCATED =
      new EventState(EventStatus.RELOCATED, START, null, null, "Hard Club", "Porto", null, null);

  @Test
  void recordsTheChangedFieldsAsTheOrganization() {
    UUID event = UUID.randomUUID();
    Instant now = Instant.parse("2026-11-10T10:00:00Z");

    EventChange change =
        EventChange.publish(
            event,
            new ChangePlan(
                ChangeType.VENUE_CHANGE,
                Severity.CRITICAL,
                SCHEDULED,
                RELOCATED,
                null,
                null,
                "Mudámos de sala."),
            "Ribeira Live",
            now);

    assertThat(change.eventId()).isEqualTo(event);
    assertThat(change.previousValue())
        .containsExactly(
            Map.entry("status", "SCHEDULED"),
            Map.entry("venueName", "Coliseu"),
            Map.entry("latitude", 41.1),
            Map.entry("longitude", -8.6));
    assertThat(change.newValue())
        .containsEntry("venueName", "Hard Club")
        .containsEntry("latitude", null);
    assertThat(change.message()).isEqualTo("Mudámos de sala.");
    assertThat(change.provenanceSourceType()).isEqualTo(SourceType.ORGANIZATION);
    assertThat(change.provenanceSourceName()).isEqualTo("Ribeira Live");
    assertThat(change.provenanceLastUpdatedAt()).isEqualTo(now);
    assertThat(change.provenanceConfirmed()).isTrue();
  }

  @Test
  void isReflectedInAnEventThatStillHasItsValues() {
    EventChange change =
        EventChange.publish(
            UUID.randomUUID(),
            new ChangePlan(
                ChangeType.VENUE_CHANGE, Severity.CRITICAL, SCHEDULED, RELOCATED, null, null, null),
            "Ribeira Live",
            Instant.EPOCH);

    assertThat(change.isReflectedIn(RELOCATED)).isTrue();
    assertThat(change.isReflectedIn(SCHEDULED)).isFalse();
  }

  @Test
  void isNotReflectedWhenItSetAFieldTheEventDoesNotHave() {
    EventChange seeded =
        new EventChange(
            UUID.randomUUID(),
            UUID.randomUUID(),
            ChangeType.TIME_CHANGE,
            Severity.INFO,
            Map.of("doorsOpenAt", "17:00"),
            Map.of("doorsOpenAt", "16:00", "gate", "A"),
            null,
            null,
            null,
            SourceType.ORGANIZATION,
            "Ribeira Live",
            Instant.EPOCH,
            true);

    assertThat(seeded.isReflectedIn(SCHEDULED)).isFalse();
  }
}
