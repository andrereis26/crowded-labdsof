package com.crowded.core.event.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventChangeTest {

  private static final Instant START = Instant.parse("2026-11-20T20:00:00Z");
  private static final EventState SCHEDULED =
      new EventState(
          EventStatus.SCHEDULED,
          new Schedule(START, null, null),
          new Venue("Coliseu", "Porto", new Coordinates(41.1, -8.6)));
  private static final EventState RELOCATED =
      new EventState(
          EventStatus.RELOCATED,
          new Schedule(START, null, null),
          new Venue("Hard Club", "Porto", null));

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
            Provenance.organization("Ribeira Live", now));

    assertThat(change.getEventId()).isEqualTo(event);
    assertThat(change.getPreviousValue())
        .containsExactly(
            Map.entry("status", "SCHEDULED"),
            Map.entry("venueName", "Coliseu"),
            Map.entry("latitude", 41.1),
            Map.entry("longitude", -8.6));
    assertThat(change.getNewValue())
        .containsEntry("venueName", "Hard Club")
        .containsEntry("latitude", null);
    assertThat(change.getMessage()).isEqualTo("Mudámos de sala.");
    assertThat(change.getProvenance())
        .isEqualTo(new Provenance(SourceType.ORGANIZATION, "Ribeira Live", now, true));
  }

  @Test
  void isReflectedInAnEventThatStillHasItsValues() {
    EventChange change =
        EventChange.publish(
            UUID.randomUUID(),
            new ChangePlan(
                ChangeType.VENUE_CHANGE, Severity.CRITICAL, SCHEDULED, RELOCATED, null, null, null),
            Provenance.organization("Ribeira Live", Instant.EPOCH));

    assertThat(change.isReflectedIn(RELOCATED)).isTrue();
    assertThat(change.isReflectedIn(SCHEDULED)).isFalse();
  }

  @Test
  void isNotReflectedWhenItSetAFieldTheEventDoesNotHave() {
    EventChange seeded =
        EventChange.restore(
            UUID.randomUUID(),
            UUID.randomUUID(),
            ChangeType.TIME_CHANGE,
            Severity.INFO,
            Map.of("doorsOpenAt", "17:00"),
            Map.of("doorsOpenAt", "16:00", "gate", "A"),
            null,
            null,
            null,
            Provenance.organization("Ribeira Live", Instant.EPOCH));

    assertThat(seeded.isReflectedIn(SCHEDULED)).isFalse();
  }
}
