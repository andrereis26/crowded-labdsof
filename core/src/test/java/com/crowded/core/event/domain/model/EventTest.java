package com.crowded.core.event.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crowded.core.shared.domain.exception.ConflictException;
import com.crowded.core.shared.domain.exception.RuleViolationException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class EventTest {

  private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
  private static final Instant START = Instant.parse("2026-11-20T20:00:00Z");
  private static final Instant DOORS = Instant.parse("2026-11-20T18:30:00Z");
  private static final Instant END = Instant.parse("2026-11-20T23:00:00Z");
  private static final Venue COLISEU =
      new Venue("Coliseu", "Rua de Passos Manuel 137, Porto", new Coordinates(41.146, -8.606));
  private static final EventState SCHEDULED =
      new EventState(EventStatus.SCHEDULED, new Schedule(START, DOORS, END), COLISEU);

  @Test
  void cancelsTheEventAsACriticalChange() {
    ChangePlan plan =
        plan(SCHEDULED, request(ChangeType.CANCELLATION, null, false, " Pedir no site ", null));

    assertThat(plan.after().status()).isEqualTo(EventStatus.CANCELLED);
    assertThat(plan.severity()).isEqualTo(Severity.CRITICAL);
    assertThat(plan.ticketStillValid()).isFalse();
    assertThat(plan.refundInfo()).isEqualTo("Pedir no site");
    assertThat(plan.before().differencesFrom(plan.after()))
        .containsExactly(Map.entry("status", "SCHEDULED"));
    assertThat(plan.after().differencesFrom(plan.before()))
        .containsExactly(Map.entry("status", "CANCELLED"));
  }

  @Test
  void findsNothingToDoWhenTheCancellationIsSentAgain() {
    EventState cancelled = SCHEDULED.inStatus(EventStatus.CANCELLED);

    assertThat(plan(cancelled, request(ChangeType.CANCELLATION, null, null, null, null), NOW))
        .isEmpty();
  }

  @Test
  void postponesAndMovesTheDoorsAndEndWithTheStart() {
    ChangePlan plan =
        plan(
            SCHEDULED,
            request(
                ChangeType.POSTPONEMENT,
                times("2026-12-04T20:00:00Z", null, null),
                true,
                null,
                "Por indisponibilidade do recinto."));

    assertThat(plan.after())
        .isEqualTo(
            new EventState(
                EventStatus.POSTPONED,
                new Schedule(
                    Instant.parse("2026-12-04T20:00:00Z"),
                    Instant.parse("2026-12-04T18:30:00Z"),
                    Instant.parse("2026-12-04T23:00:00Z")),
                COLISEU));
    assertThat(plan.severity()).isEqualTo(Severity.CRITICAL);
    assertThat(plan.message()).isEqualTo("Por indisponibilidade do recinto.");
    assertThat(plan.after().differencesFrom(plan.before()))
        .containsOnlyKeys("status", "startDateTime", "doorsOpenAt", "endDateTime")
        .containsEntry("startDateTime", "2026-12-04T20:00:00Z");
  }

  @Test
  void postponesWithTheDoorsAndEndItIsGiven() {
    ChangePlan plan =
        plan(
            SCHEDULED,
            request(
                ChangeType.POSTPONEMENT,
                times("2026-12-04T21:00:00Z", "2026-12-04T19:00:00Z", "2026-12-05T01:00:00Z"),
                null,
                null,
                null));

    assertThat(plan.after().schedule().doorsOpenAt())
        .isEqualTo(Instant.parse("2026-12-04T19:00:00Z"));
    assertThat(plan.after().schedule().end()).isEqualTo(Instant.parse("2026-12-05T01:00:00Z"));
  }

  @Test
  void findsNothingToDoWhenThePostponementIsSentAgain() {
    RequestedChange postponement =
        request(
            ChangeType.POSTPONEMENT, times("2026-12-04T20:00:00Z", null, null), null, null, null);
    ChangePlan first = plan(SCHEDULED, postponement);

    assertThat(plan(first.after(), postponement, NOW)).isEmpty();
  }

  @Test
  void refusesAPostponementWithoutALaterStart() {
    assertRefused(request(ChangeType.POSTPONEMENT, null, null, null, null), "START_REQUIRED");
    assertRefused(
        request(
            ChangeType.POSTPONEMENT, times("2026-11-19T20:00:00Z", null, null), null, null, null),
        "POSTPONEMENT_NOT_LATER");
  }

  @Test
  void changesOnlyTheDoorsAsAWarning() {
    ChangePlan plan =
        plan(
            SCHEDULED,
            request(
                ChangeType.TIME_CHANGE,
                times(null, "2026-11-20T19:00:00Z", null),
                null,
                null,
                null));

    assertThat(plan.severity()).isEqualTo(Severity.WARNING);
    assertThat(plan.after().status()).isEqualTo(EventStatus.SCHEDULED);
    assertThat(plan.after().differencesFrom(plan.before()))
        .containsExactly(Map.entry("doorsOpenAt", "2026-11-20T19:00:00Z"));
  }

  @Test
  void changesTheStartAsACriticalChangeAndKeepsTheLength() {
    ChangePlan plan =
        plan(
            SCHEDULED,
            request(
                ChangeType.TIME_CHANGE,
                times("2026-11-20T21:00:00Z", null, null),
                null,
                null,
                null));

    assertThat(plan.severity()).isEqualTo(Severity.CRITICAL);
    assertThat(plan.after().schedule().doorsOpenAt()).isEqualTo(DOORS);
    assertThat(plan.after().schedule().end()).isEqualTo(Instant.parse("2026-11-21T00:00:00Z"));
  }

  @Test
  void findsNothingToDoWhenTheTimesAreTheCurrentOnes() {
    assertThat(
            plan(
                SCHEDULED,
                request(
                    ChangeType.TIME_CHANGE,
                    times("2026-11-20T20:00:00Z", "2026-11-20T18:30:00Z", null),
                    null,
                    null,
                    null),
                NOW))
        .isEmpty();
  }

  @Test
  void refusesInconsistentTimes() {
    assertRefused(
        request(ChangeType.TIME_CHANGE, NewValues.NONE, null, null, null), "NEW_TIME_REQUIRED");
    assertRefused(
        request(
            ChangeType.TIME_CHANGE, times("2026-11-01T20:00:00Z", null, null), null, null, null),
        "START_IN_PAST");
    assertRefused(
        request(
            ChangeType.TIME_CHANGE, times(null, "2026-11-20T20:30:00Z", null), null, null, null),
        "DOORS_AFTER_START");
    assertRefused(
        request(
            ChangeType.TIME_CHANGE,
            times("2026-11-20T21:00:00Z", null, "2026-11-20T20:00:00Z"),
            null,
            null,
            null),
        "END_BEFORE_START");
  }

  @Test
  void refusesTicketInformationOutsideAPostponementOrCancellation() {
    assertRefused(
        request(
            ChangeType.TIME_CHANGE, times(null, "2026-11-20T19:00:00Z", null), true, null, null),
        "TICKET_INFO_NOT_APPLICABLE");
    assertRefused(
        request(
            ChangeType.VENUE_CHANGE,
            venue("Hard Club", "Porto", null, null),
            null,
            "Reembolso no site",
            null),
        "TICKET_INFO_NOT_APPLICABLE");
  }

  @Test
  void movesToANewVenueAndDropsTheOldCoordinates() {
    ChangePlan plan =
        plan(
            SCHEDULED,
            request(
                ChangeType.VENUE_CHANGE,
                venue(" Hard Club ", "Mercado Ferreira Borges, Porto", null, null),
                null,
                null,
                null));

    assertThat(plan.after().status()).isEqualTo(EventStatus.RELOCATED);
    assertThat(plan.after().venue().name()).isEqualTo("Hard Club");
    assertThat(plan.after().venue().coordinates()).isNull();
    assertThat(plan.severity()).isEqualTo(Severity.CRITICAL);
  }

  @Test
  void movesToANewVenueWithItsCoordinates() {
    ChangePlan plan =
        plan(
            SCHEDULED,
            request(
                ChangeType.VENUE_CHANGE,
                venue("Hard Club", "Mercado Ferreira Borges, Porto", 41.141, -8.614),
                null,
                null,
                null));

    assertThat(plan.after().venue().coordinates()).isEqualTo(new Coordinates(41.141, -8.614));
  }

  @Test
  void findsNothingToDoWhenTheVenueIsTheCurrentOne() {
    assertThat(
            plan(
                SCHEDULED,
                request(
                    ChangeType.VENUE_CHANGE,
                    venue("Coliseu", "Rua de Passos Manuel 137, Porto", null, null),
                    null,
                    null,
                    null),
                NOW))
        .isEmpty();
    assertThat(
            plan(
                SCHEDULED,
                request(
                    ChangeType.VENUE_CHANGE,
                    venue("Coliseu", "Rua de Passos Manuel 137, Porto", 41.146, -8.606),
                    null,
                    null,
                    null),
                NOW))
        .isEmpty();
  }

  @Test
  void refusesAnIncompleteVenue() {
    assertRefused(
        request(ChangeType.VENUE_CHANGE, venue("Hard Club", " ", null, null), null, null, null),
        "VENUE_REQUIRED");
    assertRefused(request(ChangeType.VENUE_CHANGE, null, null, null, null), "VENUE_REQUIRED");
    assertRefused(
        request(ChangeType.VENUE_CHANGE, venue("Hard Club", "Porto", 41.1, null), null, null, null),
        "COORDINATES_INCOMPLETE");
  }

  @ParameterizedTest
  @EnumSource(
      value = ChangeType.class,
      names = {"DETAILS_UPDATE"})
  void refusesChangesThatAreNotOfficialChanges(ChangeType type) {
    assertRefused(request(type, null, null, null, null), "UNSUPPORTED_CHANGE_TYPE");
  }

  @ParameterizedTest
  @EnumSource(
      value = EventStatus.class,
      names = {"CANCELLED", "ENDED"})
  void refusesChangesToAClosedEvent(EventStatus status) {
    RequestedChange later =
        request(
            ChangeType.TIME_CHANGE, times("2026-11-20T21:00:00Z", null, null), null, null, null);

    assertThatThrownBy(() -> plan(SCHEDULED.inStatus(status), later, NOW))
        .isInstanceOfSatisfying(
            ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("EVENT_CLOSED"));
  }

  @Test
  void dropsBlankRefundInformationAndMessage() {
    ChangePlan plan =
        plan(
            SCHEDULED.inStatus(EventStatus.ONGOING),
            request(ChangeType.CANCELLATION, null, null, "  ", "  "));

    assertThat(plan.refundInfo()).isNull();
    assertThat(plan.message()).isNull();
  }

  @Test
  void publishesAChangeAndTakesItsState() {
    UUID id = UUID.randomUUID();
    Event event = Event.restore(id, UUID.randomUUID(), SCHEDULED);

    Optional<EventChange> change =
        event.publishChange(
            request(ChangeType.CANCELLATION, null, false, null, null),
            Provenance.organization("Ribeira Live", NOW),
            NOW);

    assertThat(change).isPresent();
    assertThat(change.get().getEventId()).isEqualTo(id);
    assertThat(change.get().getType()).isEqualTo(ChangeType.CANCELLATION);
    assertThat(change.get().getProvenance().sourceName()).isEqualTo("Ribeira Live");
    assertThat(event.getState().status()).isEqualTo(EventStatus.CANCELLED);
  }

  @Test
  void publishesNothingAndKeepsItsStateWhenNothingChanges() {
    Event event = Event.restore(UUID.randomUUID(), UUID.randomUUID(), SCHEDULED);

    assertThat(
            event.publishChange(
                request(
                    ChangeType.VENUE_CHANGE,
                    venue("Coliseu", "Rua de Passos Manuel 137, Porto", null, null),
                    null,
                    null,
                    null),
                Provenance.organization("Ribeira Live", NOW),
                NOW))
        .isEmpty();
    assertThat(event.getState()).isEqualTo(SCHEDULED);
  }

  @Test
  void keepsItsStateWhenAChangeIsRefused() {
    Event event = Event.restore(UUID.randomUUID(), UUID.randomUUID(), SCHEDULED);
    RequestedChange earlier =
        request(
            ChangeType.TIME_CHANGE, times("2026-11-01T20:00:00Z", null, null), null, null, null);

    assertThatThrownBy(
            () -> event.publishChange(earlier, Provenance.organization("Ribeira Live", NOW), NOW))
        .isInstanceOf(RuleViolationException.class);
    assertThat(event.getState()).isEqualTo(SCHEDULED);
  }

  @Test
  void findsTheEarlierChangeOfARepeatedRequest() {
    Event event = Event.restore(UUID.randomUUID(), UUID.randomUUID(), SCHEDULED);
    EventChange cancellation =
        event
            .publishChange(
                request(ChangeType.CANCELLATION, null, null, null, null),
                Provenance.organization("Ribeira Live", NOW),
                NOW)
            .orElseThrow();

    assertThat(event.repeatedChange(Optional.of(cancellation))).isSameAs(cancellation);
  }

  @Test
  void refusesARepeatedRequestThatNoChangeExplains() {
    Event event = Event.restore(UUID.randomUUID(), UUID.randomUUID(), SCHEDULED);
    Event other = Event.restore(UUID.randomUUID(), UUID.randomUUID(), SCHEDULED);
    EventChange othersChange =
        other
            .publishChange(
                request(ChangeType.CANCELLATION, null, null, null, null),
                Provenance.organization("Ribeira Live", NOW),
                NOW)
            .orElseThrow();

    assertThatThrownBy(() -> event.repeatedChange(Optional.empty()))
        .isInstanceOfSatisfying(
            RuleViolationException.class,
            e -> assertThat(e.getCode()).isEqualTo("NOTHING_CHANGES"));
    assertThatThrownBy(() -> event.repeatedChange(Optional.of(othersChange)))
        .isInstanceOf(RuleViolationException.class);
  }

  @Test
  void isTheSameEventWhateverItsState() {
    UUID id = UUID.randomUUID();

    assertThat(Event.restore(id, null, SCHEDULED))
        .isEqualTo(Event.restore(id, null, SCHEDULED.inStatus(EventStatus.ENDED)))
        .hasSameHashCodeAs(Event.restore(id, null, SCHEDULED))
        .isNotEqualTo(Event.restore(UUID.randomUUID(), null, SCHEDULED));
  }

  private static Optional<ChangePlan> plan(
      EventState before, RequestedChange request, Instant now) {
    return Event.restore(UUID.randomUUID(), UUID.randomUUID(), before).plan(request, now);
  }

  private static ChangePlan plan(EventState before, RequestedChange request) {
    Optional<ChangePlan> plan = plan(before, request, NOW);
    assertThat(plan).isPresent();
    return plan.get();
  }

  private static void assertRefused(RequestedChange request, String code) {
    assertThatThrownBy(() -> plan(SCHEDULED, request, NOW))
        .isInstanceOfSatisfying(
            RuleViolationException.class, e -> assertThat(e.getCode()).isEqualTo(code));
  }

  private static RequestedChange request(
      ChangeType type, NewValues value, Boolean ticketStillValid, String refund, String message) {
    return new RequestedChange(type, value, ticketStillValid, refund, message);
  }

  private static NewValues times(String start, String doors, String end) {
    return new NewValues(instant(start), instant(doors), instant(end), null, null, null, null);
  }

  private static NewValues venue(String name, String address, Double latitude, Double longitude) {
    return new NewValues(null, null, null, name, address, latitude, longitude);
  }

  private static Instant instant(String value) {
    return value == null ? null : Instant.parse(value);
  }
}
