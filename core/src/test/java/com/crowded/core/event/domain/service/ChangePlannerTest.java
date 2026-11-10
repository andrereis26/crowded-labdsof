package com.crowded.core.event.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crowded.core.event.domain.model.ChangePlan;
import com.crowded.core.event.domain.model.ChangeType;
import com.crowded.core.event.domain.model.EventState;
import com.crowded.core.event.domain.model.EventStatus;
import com.crowded.core.event.domain.model.NewValues;
import com.crowded.core.event.domain.model.RequestedChange;
import com.crowded.core.event.domain.model.Severity;
import com.crowded.core.shared.domain.exception.ConflictException;
import com.crowded.core.shared.domain.exception.RuleViolationException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ChangePlannerTest {

  private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
  private static final Instant START = Instant.parse("2026-11-20T20:00:00Z");
  private static final Instant DOORS = Instant.parse("2026-11-20T18:30:00Z");
  private static final Instant END = Instant.parse("2026-11-20T23:00:00Z");
  private static final EventState SCHEDULED =
      new EventState(
          EventStatus.SCHEDULED,
          START,
          DOORS,
          END,
          "Coliseu",
          "Rua de Passos Manuel 137, Porto",
          41.146,
          -8.606);

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
    EventState cancelled = withStatus(SCHEDULED, EventStatus.CANCELLED);

    assertThat(
            ChangePlanner.plan(
                cancelled, request(ChangeType.CANCELLATION, null, null, null, null), NOW))
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
                Instant.parse("2026-12-04T20:00:00Z"),
                Instant.parse("2026-12-04T18:30:00Z"),
                Instant.parse("2026-12-04T23:00:00Z"),
                SCHEDULED.venueName(),
                SCHEDULED.address(),
                SCHEDULED.latitude(),
                SCHEDULED.longitude()));
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

    assertThat(plan.after().doorsOpenAt()).isEqualTo(Instant.parse("2026-12-04T19:00:00Z"));
    assertThat(plan.after().endDateTime()).isEqualTo(Instant.parse("2026-12-05T01:00:00Z"));
  }

  @Test
  void findsNothingToDoWhenThePostponementIsSentAgain() {
    RequestedChange postponement =
        request(
            ChangeType.POSTPONEMENT, times("2026-12-04T20:00:00Z", null, null), null, null, null);
    ChangePlan first = plan(SCHEDULED, postponement);

    assertThat(ChangePlanner.plan(first.after(), postponement, NOW)).isEmpty();
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
    assertThat(plan.after().doorsOpenAt()).isEqualTo(DOORS);
    assertThat(plan.after().endDateTime()).isEqualTo(Instant.parse("2026-11-21T00:00:00Z"));
  }

  @Test
  void findsNothingToDoWhenTheTimesAreTheCurrentOnes() {
    assertThat(
            ChangePlanner.plan(
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
    assertThat(plan.after().venueName()).isEqualTo("Hard Club");
    assertThat(plan.after().latitude()).isNull();
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

    assertThat(plan.after().latitude()).isEqualTo(41.141);
    assertThat(plan.after().longitude()).isEqualTo(-8.614);
  }

  @Test
  void findsNothingToDoWhenTheVenueIsTheCurrentOne() {
    assertThat(
            ChangePlanner.plan(
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
            ChangePlanner.plan(
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
      names = {"CONFIRMATION", "DETAILS_UPDATE"})
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

    assertThatThrownBy(() -> ChangePlanner.plan(withStatus(SCHEDULED, status), later, NOW))
        .isInstanceOfSatisfying(
            ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("EVENT_CLOSED"));
  }

  @Test
  void refusesChangesToAProposedEvent() {
    EventState proposed =
        new EventState(EventStatus.PROPOSED, null, null, null, null, null, null, null);

    assertThatThrownBy(
            () ->
                ChangePlanner.plan(
                    proposed, request(ChangeType.CANCELLATION, null, null, null, null), NOW))
        .isInstanceOfSatisfying(
            ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("EVENT_CLOSED"));
  }

  @Test
  void dropsBlankRefundInformationAndMessage() {
    ChangePlan plan =
        plan(
            withStatus(SCHEDULED, EventStatus.ONGOING),
            request(ChangeType.CANCELLATION, null, null, "  ", "  "));

    assertThat(plan.refundInfo()).isNull();
    assertThat(plan.message()).isNull();
  }

  private static ChangePlan plan(EventState before, RequestedChange request) {
    Optional<ChangePlan> plan = ChangePlanner.plan(before, request, NOW);
    assertThat(plan).isPresent();
    return plan.get();
  }

  private static void assertRefused(RequestedChange request, String code) {
    assertThatThrownBy(() -> ChangePlanner.plan(SCHEDULED, request, NOW))
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

  private static EventState withStatus(EventState state, EventStatus status) {
    return new EventState(
        status,
        state.startDateTime(),
        state.doorsOpenAt(),
        state.endDateTime(),
        state.venueName(),
        state.address(),
        state.latitude(),
        state.longitude());
  }
}
