package com.crowded.core.event.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crowded.core.shared.domain.exception.ConflictException;
import com.crowded.core.shared.domain.exception.RuleViolationException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The queue waiting times an organization publishes at its event's points. */
class EventQueueTimesTest {

  private static final Instant NOW = Instant.parse("2026-11-20T21:00:00Z");
  private static final Provenance RIBEIRA = Provenance.organization("Ribeira Live", NOW);

  @Test
  void aPointStartsWithoutAWaitingTime() {
    assertThat(event(EventStatus.ONGOING).getQueuePoints().timed()).isEmpty();
  }

  @Test
  void publishesTheTimesOfTheNamedPointsAndKeepsTheOthers() {
    Event event = event(EventStatus.ONGOING);
    event.publishQueueTimes(List.of(new QueueWait("Bar", 20)), RIBEIRA);
    Provenance later = Provenance.organization("Ribeira Live", NOW.plusSeconds(300));

    event.publishQueueTimes(List.of(new QueueWait(" Porta A ", 5)), later);

    assertThat(event.getQueuePoints().points())
        .containsExactly(
            new QueuePoint("Porta A", new QueueTime(5, later)),
            new QueuePoint("Bar", new QueueTime(20, RIBEIRA)),
            new QueuePoint("WC", null));
    assertThat(event.getQueuePoints().timed())
        .extracting(QueuePoint::name)
        .containsExactly("Porta A", "Bar");
  }

  @Test
  void theSameTimeAgainTellsItIsStillCurrent() {
    Event event = event(EventStatus.ONGOING);
    event.publishQueueTimes(List.of(new QueueWait("Bar", 0)), RIBEIRA);
    Provenance later = Provenance.organization("Ribeira Live", NOW.plusSeconds(600));

    event.publishQueueTimes(List.of(new QueueWait("Bar", 0)), later);

    assertThat(event.getQueuePoints().timed().getFirst().time()).isEqualTo(new QueueTime(0, later));
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 601})
  void refusesAWaitThatIsNegativeOrTooLong(int minutes) {
    assertThatThrownBy(() -> new QueueWait("Bar", minutes))
        .isInstanceOf(RuleViolationException.class)
        .hasFieldOrPropertyWithValue("code", "WAIT_MINUTES_INVALID");
    assertThatThrownBy(() -> new QueueTime(minutes, RIBEIRA))
        .hasFieldOrPropertyWithValue("code", "WAIT_MINUTES_INVALID");
  }

  @Test
  void refusesNoTimeAPointOfAnotherNameOrTheSamePointTwice() {
    Event event = event(EventStatus.ONGOING);

    assertThatThrownBy(() -> event.publishQueueTimes(List.of(), RIBEIRA))
        .hasFieldOrPropertyWithValue("code", "QUEUE_TIMES_REQUIRED");
    assertThatThrownBy(
            () -> event.publishQueueTimes(List.of(new QueueWait("Bilheteira", 5)), RIBEIRA))
        .isInstanceOf(RuleViolationException.class)
        .hasFieldOrPropertyWithValue("code", "QUEUE_POINT_UNKNOWN");
    assertThatThrownBy(
            () ->
                event.publishQueueTimes(
                    List.of(new QueueWait("Bar", 5), new QueueWait("Bar", 10)), RIBEIRA))
        .hasFieldOrPropertyWithValue("code", "QUEUE_POINT_REPEATED");
    assertThat(event.getQueuePoints().timed()).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(
      value = EventStatus.class,
      names = {"CANCELLED", "ENDED"})
  void refusesTimesForAnEventThatIsOver(EventStatus status) {
    Event event = event(status);

    assertThatThrownBy(() -> event.publishQueueTimes(List.of(new QueueWait("Bar", 5)), RIBEIRA))
        .isInstanceOf(ConflictException.class)
        .hasFieldOrPropertyWithValue("code", "EVENT_CLOSED");
    assertThat(event.getQueuePoints().timed()).isEmpty();
  }

  @Test
  void aPointKeepsItsTimeWhileTheOrganizationKeepsItsName() {
    Event event = event(EventStatus.ONGOING);
    event.publishQueueTimes(List.of(new QueueWait("Bar", 20), new QueueWait("WC", 5)), RIBEIRA);

    event.defineQueuePoints(QueuePoints.named(List.of("WC", "Porta A", "bar")));

    assertThat(event.getQueuePoints().points())
        .containsExactly(
            new QueuePoint("WC", new QueueTime(5, RIBEIRA)),
            new QueuePoint("Porta A", null),
            new QueuePoint("bar", null));
  }

  private static Event event(EventStatus status) {
    Event event =
        StoredEvents.restore(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new EventState(
                status,
                new Schedule(NOW.minusSeconds(3600), null, NOW.plusSeconds(7200)),
                new Venue("Coliseu", "Porto", null)));
    event.defineQueuePoints(QueuePoints.named(List.of("Porta A", "Bar", "WC")));
    return event;
  }
}
