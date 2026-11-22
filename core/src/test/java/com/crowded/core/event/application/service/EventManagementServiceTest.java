package com.crowded.core.event.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.crowded.core.audit.application.service.AuditLog;
import com.crowded.core.connection.domain.model.ActiveAlert;
import com.crowded.core.connection.domain.repository.ActiveAlertRepository;
import com.crowded.core.connection.domain.repository.ConnectionRepository;
import com.crowded.core.event.application.dto.ConfirmationsInput;
import com.crowded.core.event.application.dto.CorrectEventCommand;
import com.crowded.core.event.application.dto.ManagedEvent;
import com.crowded.core.event.application.dto.RegisterEventCommand;
import com.crowded.core.event.application.dto.VenueInput;
import com.crowded.core.event.domain.model.Admission;
import com.crowded.core.event.domain.model.ConfirmationRules;
import com.crowded.core.event.domain.model.District;
import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.EventCode;
import com.crowded.core.event.domain.model.EventState;
import com.crowded.core.event.domain.model.EventStatus;
import com.crowded.core.event.domain.model.EventType;
import com.crowded.core.event.domain.model.ReportCategory;
import com.crowded.core.event.domain.model.Schedule;
import com.crowded.core.event.domain.model.Severity;
import com.crowded.core.event.domain.model.StoredEvents;
import com.crowded.core.event.domain.model.Venue;
import com.crowded.core.event.domain.repository.EventImageRepository;
import com.crowded.core.event.domain.repository.EventRepository;
import com.crowded.core.event.domain.repository.EventUpdates;
import com.crowded.core.event.domain.repository.ReportCategoryRepository;
import com.crowded.core.shared.domain.exception.ConflictException;
import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.shared.domain.exception.NotFoundException;
import com.crowded.core.user.domain.model.Role;
import com.crowded.core.user.domain.model.User;
import com.crowded.core.user.domain.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EventManagementServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
  private static final Instant START = Instant.parse("2026-12-12T21:00:00Z");
  private static final UUID ORGANIZATION = UUID.randomUUID();
  private static final UUID MEMBER = UUID.randomUUID();
  private static final List<ReportCategory> CATALOG =
      List.of(
          new ReportCategory("CROWD_LEVEL", false, true),
          new ReportCategory("ON_SITE_PROBLEM", false, false),
          new ReportCategory("LOST_AND_FOUND", true, false));

  private final EventRepository events = mock(EventRepository.class);
  private final ReportCategoryRepository categories = mock(ReportCategoryRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final ConnectionRepository connections = mock(ConnectionRepository.class);
  private final EventImageRepository images = mock(EventImageRepository.class);
  private final ActiveAlertRepository alerts = mock(ActiveAlertRepository.class);
  private final EventUpdates updates = mock(EventUpdates.class);
  private final AuditLog audit = mock(AuditLog.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final EventManagementService service =
      new EventManagementService(
          events,
          categories,
          users,
          connections,
          images,
          alerts,
          new EventAccess(events, users, connections, meters),
          updates,
          audit,
          Clock.fixed(NOW, ZoneOffset.UTC),
          meters);

  @BeforeEach
  void givenAMemberAndTheCategories() {
    when(users.findById(MEMBER))
        .thenReturn(
            Optional.of(User.restore(MEMBER, ORGANIZATION, Role.ORGANIZATION_MEMBER, true)));
    when(categories.findAll()).thenReturn(CATALOG);
  }

  @Test
  void registersTheEventForTheMembersOrganizationAndAuditsIt() {
    ManagedEvent registered = service.register(command(null));

    ArgumentCaptor<Event> added = ArgumentCaptor.forClass(Event.class);
    verify(events).add(added.capture());
    Event event = added.getValue();
    assertThat(registered).isEqualTo(new ManagedEvent(event, 0, null, List.of()));
    assertThat(event.getOrganizationId()).isEqualTo(ORGANIZATION);
    assertThat(event.getState().status()).isEqualTo(EventStatus.SCHEDULED);
    assertThat(event.getAdmission().accessCode()).isEqualTo("SAOJOAO26");
    assertThat(event.getState().venue().name()).isEqualTo("Parque da Cidade");
    assertThat(event.getQueuePoints().names()).containsExactly("Porta A", "Bar");
    // Without a choice, the categories enabled by default ask for confirmations.
    assertThat(event.getConfirmations().promptCategories()).containsExactly("CROWD_LEVEL");
    verify(events).lockCodes();
    verify(events).isCodeUsedByAnotherUpcomingEvent(new EventCode("SAOJOAO26"), null, NOW);
    verify(audit)
        .record(
            MEMBER,
            event.getId(),
            "EVENT_CREATED",
            "Event",
            event.getId(),
            Map.of("type", "PUBLIC"));
    assertThat(meters.get("crowded.events.created").tag("type", "PUBLIC").counter().count())
        .isOne();
  }

  @Test
  void registersTheConfirmationSettingsTheMemberChose() {
    service.register(command(new ConfirmationsInput(Set.of("ON_SITE_PROBLEM"), 1000, 12, 1)));

    ArgumentCaptor<Event> added = ArgumentCaptor.forClass(Event.class);
    verify(events).add(added.capture());
    assertThat(added.getValue().getConfirmations().promptCategories())
        .containsExactly("ON_SITE_PROBLEM");
    assertThat(added.getValue().getConfirmations().rules())
        .isEqualTo(new ConfirmationRules(1000, 12, 1));
  }

  @Test
  void refusesACodeAnotherUpcomingEventUsesWithoutSavingAnything() {
    when(events.isCodeUsedByAnotherUpcomingEvent(any(), any(), any())).thenReturn(true);

    assertThatThrownBy(() -> service.register(command(null)))
        .isInstanceOf(ConflictException.class)
        .hasFieldOrPropertyWithValue("code", "EVENT_CODE_TAKEN");
    verify(events, never()).add(any());
  }

  @Test
  void refusesAUserWhoIsNotAnActiveMember() {
    UUID participant = UUID.randomUUID();
    when(users.findById(participant))
        .thenReturn(Optional.of(User.restore(participant, null, Role.PARTICIPANT, true)));
    RegisterEventCommand byParticipant = withUser(command(null), participant);
    RegisterEventCommand byNobody = withUser(command(null), UUID.randomUUID());

    for (RegisterEventCommand refused : List.of(byParticipant, byNobody)) {
      assertThatThrownBy(() -> service.register(refused))
          .isInstanceOf(NotAllowedException.class)
          .hasFieldOrPropertyWithValue("code", "NOT_ORGANIZATION_MEMBER");
    }
    verify(events, never()).add(any());
  }

  @Test
  void correctsOnlyTheFieldsSentAndKeepsTheConnections() {
    Event event = givenEvent();
    when(connections.countByEvent(List.of(event.getId()))).thenReturn(Map.of(event.getId(), 42L));

    ManagedEvent corrected =
        service.correct(
            new CorrectEventCommand(
                MEMBER,
                event.getId(),
                "Concerto de Inverno",
                null,
                5000,
                "inverno-26",
                null,
                List.of("Porta B"),
                new ConfirmationsInput(Set.of(), 200, 5, 5),
                null,
                null));

    assertThat(corrected.connectedUsers()).isEqualTo(42);
    assertThat(event.getProfile().name()).isEqualTo("Concerto de Inverno");
    assertThat(event.getProfile().description()).isEqualTo("Um concerto no Porto.");
    assertThat(event.getCapacity().people()).isEqualTo(5000);
    assertThat(event.getAdmission().ticketEventCode()).isEqualTo("INVERNO-26");
    assertThat(event.getQueuePoints().names()).containsExactly("Porta B");
    assertThat(event.getConfirmations().promptCategories()).isEmpty();
    verify(events)
        .isCodeUsedByAnotherUpcomingEvent(new EventCode("INVERNO-26"), event.getId(), NOW);
    verify(events).save(event);
    verify(updates).announce(event.getId());
    verify(audit)
        .record(
            MEMBER,
            event.getId(),
            "EVENT_UPDATED",
            "Event",
            event.getId(),
            Map.of("fields", "profile,capacity,code,queuePoints,confirmations"));
  }

  @Test
  void turnsATicketedEventPublicAndMovesItToAnotherDistrict() {
    Event event = givenEvent();

    service.correct(
        new CorrectEventCommand(
            MEMBER,
            event.getId(),
            null,
            null,
            null,
            null,
            "INVERNO-26",
            null,
            null,
            EventType.PUBLIC,
            "Braga"));

    assertThat(event.getAdmission())
        .isEqualTo(new Admission(EventType.PUBLIC, new EventCode("INVERNO-26")));
    assertThat(event.getDistrict()).isEqualTo(new District("Braga"));
    verify(events)
        .isCodeUsedByAnotherUpcomingEvent(new EventCode("INVERNO-26"), event.getId(), NOW);
    verify(audit)
        .record(
            MEMBER,
            event.getId(),
            "EVENT_UPDATED",
            "Event",
            event.getId(),
            Map.of("fields", "type,code,district"));
  }

  @Test
  void leavesTheCodeUncheckedWhenItIsNotCorrected() {
    Event event = givenEvent();

    service.correct(
        new CorrectEventCommand(
            MEMBER, event.getId(), null, "Novo texto.", null, null, null, null, null, null, null));

    assertThat(event.getProfile().description()).isEqualTo("Novo texto.");
    verify(events, never()).lockCodes();
    verify(events).save(event);
  }

  @Test
  void refusesACodeInTheOtherTypesFieldWithoutCheckingIt() {
    Event event = givenEvent();

    assertThatThrownBy(
            () ->
                service.correct(
                    new CorrectEventCommand(
                        MEMBER,
                        event.getId(),
                        null,
                        null,
                        null,
                        null,
                        "PUB-26",
                        null,
                        null,
                        null,
                        null)))
        .hasFieldOrPropertyWithValue("code", "CODE_TYPE_MISMATCH");
    verify(events, never()).lockCodes();
    verify(events, never()).save(any());
  }

  @Test
  void refusesToCorrectAnotherOrganizationsEventOrOneThatDoesNotExist() {
    Event other = StoredEvents.restore(UUID.randomUUID(), UUID.randomUUID(), scheduled());
    when(events.findByIdForUpdate(other.getId())).thenReturn(Optional.of(other));

    assertThatThrownBy(
            () ->
                service.correct(
                    new CorrectEventCommand(
                        MEMBER,
                        other.getId(),
                        "Meu",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)))
        .isInstanceOf(NotAllowedException.class)
        .hasFieldOrPropertyWithValue("code", "NOT_EVENT_ORGANIZATION");
    assertThatThrownBy(
            () ->
                service.correct(
                    new CorrectEventCommand(
                        MEMBER,
                        UUID.randomUUID(),
                        "Meu",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)))
        .isInstanceOf(NotFoundException.class);
    verify(events, never()).save(any());
    verifyNoInteractions(updates);
  }

  @Test
  void deletesAnEventNobodyIsConnectedToAndAuditsItApart() {
    Event event = givenEvent();

    service.delete(MEMBER, event.getId());

    verify(events).delete(event);
    verify(updates).announce(event.getId());
    verify(audit)
        .record(
            MEMBER,
            null,
            "EVENT_DELETED",
            "Event",
            event.getId(),
            Map.of("name", event.getProfile().name()));
  }

  @Test
  void refusesToDeleteAnEventWithParticipantsOrOfAnotherOrganization() {
    Event event = givenEvent();
    when(connections.countByEvent(List.of(event.getId()))).thenReturn(Map.of(event.getId(), 1L));
    Event other = StoredEvents.restore(UUID.randomUUID(), UUID.randomUUID(), scheduled());
    when(events.findByIdForUpdate(other.getId())).thenReturn(Optional.of(other));

    assertThatThrownBy(() -> service.delete(MEMBER, event.getId()))
        .isInstanceOf(ConflictException.class)
        .hasFieldOrPropertyWithValue("code", "EVENT_HAS_PARTICIPANTS");
    assertThatThrownBy(() -> service.delete(MEMBER, other.getId()))
        .isInstanceOf(NotAllowedException.class);
    assertThatThrownBy(() -> service.delete(MEMBER, UUID.randomUUID()))
        .isInstanceOf(NotFoundException.class);
    verify(events, never()).delete(any());
    verifyNoInteractions(updates);
  }

  @Test
  void listsTheMembersOrganizationsEventsWithTheirConnectionsAndActiveAlerts() {
    Event first = StoredEvents.restore(UUID.randomUUID(), ORGANIZATION, scheduled());
    Event second = StoredEvents.restore(UUID.randomUUID(), ORGANIZATION, scheduled());
    when(events.findByOrganization(ORGANIZATION)).thenReturn(List.of(first, second));
    when(connections.countByEvent(anyCollection())).thenReturn(Map.of(first.getId(), 7L));
    when(images.findUpdatedAt(anyCollection())).thenReturn(Map.of(second.getId(), NOW));
    ActiveAlert full =
        new ActiveAlert(
            UUID.randomUUID(),
            "CAPACITY_REACHED",
            Severity.WARNING,
            "Lotação.",
            NOW,
            "Ribeira Live");
    when(alerts.findByEvents(anyCollection())).thenReturn(Map.of(first.getId(), List.of(full)));

    assertThat(service.listForMember(MEMBER))
        .containsExactly(
            new ManagedEvent(first, 7, null, List.of(full)),
            new ManagedEvent(second, 0, NOW, List.of()));
    verify(connections).countByEvent(eq(List.of(first.getId(), second.getId())));
  }

  private Event givenEvent() {
    Event event = StoredEvents.restore(UUID.randomUUID(), ORGANIZATION, scheduled());
    when(events.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
    return event;
  }

  private static EventState scheduled() {
    return new EventState(
        EventStatus.SCHEDULED,
        new Schedule(START, null, null),
        new Venue("Coliseu", "Porto", null));
  }

  private static RegisterEventCommand command(ConfirmationsInput confirmations) {
    return new RegisterEventCommand(
        MEMBER,
        "São João no Parque",
        "Concerto gratuito.",
        EventType.PUBLIC,
        "saojoao26",
        START,
        START.minusSeconds(3600),
        START.plusSeconds(5 * 3600),
        new VenueInput("Parque da Cidade", "Estrada Interior", "Porto", 41.17, -8.678),
        20000,
        List.of("Porta A", "Bar"),
        confirmations);
  }

  private static RegisterEventCommand withUser(RegisterEventCommand command, UUID userId) {
    return new RegisterEventCommand(
        userId,
        command.name(),
        command.description(),
        command.type(),
        command.code(),
        command.startDateTime(),
        command.doorsOpenAt(),
        command.endDateTime(),
        command.venue(),
        command.capacity(),
        command.queuePoints(),
        command.confirmations());
  }
}
