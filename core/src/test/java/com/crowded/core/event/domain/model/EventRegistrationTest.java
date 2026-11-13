package com.crowded.core.event.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crowded.core.shared.domain.exception.RuleViolationException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Registering an event and correcting the details that are not an official change. */
class EventRegistrationTest {

  private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
  private static final Instant START = Instant.parse("2026-12-12T21:00:00Z");
  private static final Schedule SCHEDULE =
      new Schedule(START, START.minusSeconds(3600), START.plusSeconds(5 * 3600));
  private static final Venue VENUE =
      new Venue("Pavilhão Rosa Mota", "Rua de Dom Manuel II", new Coordinates(41.148, -8.625));
  private static final UUID ORGANIZATION = UUID.randomUUID();
  private static final ConfirmationSettings SETTINGS =
      new ConfirmationSettings(Set.of("CROWD_LEVEL"), ConfirmationRules.DEFAULT);

  @Test
  void registersAScheduledEventOfTheOrganization() {
    Event event = register(SCHEDULE, VENUE, new Capacity(4500));

    assertThat(event.getId()).isNotNull();
    assertThat(event.getOrganizationId()).isEqualTo(ORGANIZATION);
    assertThat(event.getState()).isEqualTo(new EventState(EventStatus.SCHEDULED, SCHEDULE, VENUE));
    assertThat(event.getProfile()).isEqualTo(new EventProfile("Margens Inverno", "Concerto."));
    assertThat(event.getAdmission().ticketEventCode()).isEqualTo("MARGINV26");
    assertThat(event.getAdmission().accessCode()).isNull();
    assertThat(event.getDistrict()).isEqualTo(new District("Porto"));
    assertThat(event.getCapacity()).isEqualTo(new Capacity(4500));
    assertThat(event.getQueuePoints().names()).containsExactly("Porta A", "Bar");
    assertThat(event.getConfirmations()).isEqualTo(SETTINGS);
    assertThat(register(SCHEDULE, VENUE, new Capacity(4500))).isNotEqualTo(event);
  }

  @Test
  void refusesAnEventThatStartsInThePast() {
    Schedule past = new Schedule(NOW.minusSeconds(60), null, NOW.plusSeconds(3600));

    assertThatThrownBy(() -> register(past, VENUE, new Capacity(10)))
        .isInstanceOf(RuleViolationException.class)
        .hasFieldOrPropertyWithValue("code", "START_IN_PAST");
  }

  @Test
  void refusesAnEventWithoutItsEndLocationOrCapacity() {
    assertThatThrownBy(() -> register(new Schedule(START, null, null), VENUE, new Capacity(10)))
        .hasFieldOrPropertyWithValue("code", "END_REQUIRED");
    assertThatThrownBy(
            () -> register(SCHEDULE, new Venue("Pavilhão", "Rua", null), new Capacity(10)))
        .hasFieldOrPropertyWithValue("code", "LOCATION_REQUIRED");
    assertThatThrownBy(() -> register(SCHEDULE, VENUE, null))
        .hasFieldOrPropertyWithValue("code", "CAPACITY_REQUIRED");
  }

  @Test
  void correctsTheDetailsThatAreNotAnOfficialChange() {
    Event event = register(SCHEDULE, VENUE, new Capacity(4500));
    EventState before = event.getState();

    event.correctProfile(new EventProfile("Margens de Inverno", "Concerto no Porto."));
    event.changeCapacity(new Capacity(5000));
    event.replaceCode(new EventCode("margins-26"));
    event.defineQueuePoints(QueuePoints.named(List.of("Porta B")));
    event.configureConfirmations(
        new ConfirmationSettings(Set.of(), new ConfirmationRules(1000, 10, 1)));

    assertThat(event.getProfile().name()).isEqualTo("Margens de Inverno");
    assertThat(event.getCapacity().people()).isEqualTo(5000);
    assertThat(event.getAdmission())
        .isEqualTo(new Admission(EventType.TICKETED, new EventCode("MARGINS-26")));
    assertThat(event.getQueuePoints().names()).containsExactly("Porta B");
    assertThat(event.getConfirmations().promptsFor("CROWD_LEVEL")).isFalse();
    assertThat(event.getState()).isEqualTo(before);
  }

  @Test
  void aPublicEventHasAnAccessCodeInsteadOfATicketCode() {
    Admission admission = new Admission(EventType.PUBLIC, new EventCode("saojoao24"));

    assertThat(admission.accessCode()).isEqualTo("SAOJOAO24");
    assertThat(admission.ticketEventCode()).isNull();
    assertThatThrownBy(() -> new Admission(null, new EventCode("SAOJOAO24")))
        .hasFieldOrPropertyWithValue("code", "TYPE_REQUIRED");
  }

  private static Event register(Schedule schedule, Venue venue, Capacity capacity) {
    return Event.register(
        ORGANIZATION,
        new EventProfile(" Margens Inverno ", "Concerto."),
        new Admission(EventType.TICKETED, new EventCode("marginv26")),
        schedule,
        venue,
        new District("Porto"),
        capacity,
        QueuePoints.named(List.of("Porta A", "Bar")),
        SETTINGS,
        NOW);
  }
}
