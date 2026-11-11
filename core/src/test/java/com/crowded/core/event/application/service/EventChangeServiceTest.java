package com.crowded.core.event.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.crowded.core.audit.application.service.AuditLog;
import com.crowded.core.event.application.dto.PublishChangeCommand;
import com.crowded.core.event.application.dto.PublishedChange;
import com.crowded.core.event.domain.model.ChangePlan;
import com.crowded.core.event.domain.model.ChangeType;
import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.EventChange;
import com.crowded.core.event.domain.model.EventState;
import com.crowded.core.event.domain.model.EventStatus;
import com.crowded.core.event.domain.model.RequestedChange;
import com.crowded.core.event.domain.model.Severity;
import com.crowded.core.event.domain.model.SourceType;
import com.crowded.core.event.domain.repository.EventChangeRepository;
import com.crowded.core.event.domain.repository.EventRepository;
import com.crowded.core.organization.domain.model.Organization;
import com.crowded.core.organization.domain.repository.OrganizationRepository;
import com.crowded.core.outbox.application.service.NotificationRequester;
import com.crowded.core.shared.domain.exception.DomainException;
import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.shared.domain.exception.NotFoundException;
import com.crowded.core.shared.domain.exception.RuleViolationException;
import com.crowded.core.user.domain.model.Role;
import com.crowded.core.user.domain.model.User;
import com.crowded.core.user.domain.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EventChangeServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
  private static final UUID ORGANIZATION = UUID.randomUUID();
  private static final UUID MEMBER = UUID.randomUUID();
  private static final EventState SCHEDULED =
      new EventState(
          EventStatus.SCHEDULED,
          Instant.parse("2026-11-20T20:00:00Z"),
          null,
          null,
          "Coliseu",
          "Porto",
          null,
          null);
  private static final RequestedChange CANCELLATION =
      new RequestedChange(ChangeType.CANCELLATION, null, false, "Reembolso no site", null);

  private final EventRepository events = mock(EventRepository.class);
  private final EventChangeRepository changes = mock(EventChangeRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final OrganizationRepository organizations = mock(OrganizationRepository.class);
  private final NotificationRequester notifications = mock(NotificationRequester.class);
  private final AuditLog audit = mock(AuditLog.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final EventChangeService service =
      new EventChangeService(
          events,
          changes,
          users,
          organizations,
          notifications,
          audit,
          Clock.fixed(NOW, ZoneOffset.UTC),
          meters);

  private final Event event = new Event(UUID.randomUUID(), ORGANIZATION, SCHEDULED);

  @Test
  void recordsTheChangeUpdatesTheEventAndAsksForNotifications() {
    givenEventAndMember();
    when(organizations.findById(ORGANIZATION))
        .thenReturn(Optional.of(new Organization(ORGANIZATION, "Ribeira Live")));

    PublishedChange published = service.publish(command(CANCELLATION));

    ArgumentCaptor<EventChange> saved = ArgumentCaptor.forClass(EventChange.class);
    verify(changes).save(saved.capture());
    EventChange change = saved.getValue();
    assertThat(published).isEqualTo(new PublishedChange(change.id(), Severity.CRITICAL, true));
    assertThat(change.type()).isEqualTo(ChangeType.CANCELLATION);
    assertThat(change.newValue()).containsExactly(Map.entry("status", "CANCELLED"));
    assertThat(change.ticketStillValid()).isFalse();
    assertThat(change.refundInfo()).isEqualTo("Reembolso no site");
    assertThat(change.provenanceSourceName()).isEqualTo("Ribeira Live");
    assertThat(event.getState().status()).isEqualTo(EventStatus.CANCELLED);
    verify(events).save(event);
    verify(notifications).request("OFFICIAL_CHANGE", event.getId(), change.id());
    verify(audit)
        .record(
            MEMBER,
            event.getId(),
            "EVENT_CHANGE_PUBLISHED",
            "EventChange",
            change.id(),
            Map.of("type", "CANCELLATION", "severity", "CRITICAL"));
    assertThat(
            meters
                .get("crowded.event.changes")
                .tag("type", "CANCELLATION")
                .tag("severity", "CRITICAL")
                .counter()
                .count())
        .isOne();
  }

  @Test
  void returnsTheFirstChangeWhenItIsSentAgain() {
    givenEventAndMember();
    EventChange first = cancellationOf(event.getState());
    event.apply(plan(event.getState(), cancelled()));
    when(changes.findLatest(event.getId(), ChangeType.CANCELLATION)).thenReturn(Optional.of(first));

    PublishedChange published = service.publish(command(CANCELLATION));

    assertThat(published).isEqualTo(new PublishedChange(first.id(), Severity.CRITICAL, false));
    verify(changes, never()).save(any());
    verify(events, never()).save(any());
    verifyNoInteractions(notifications, audit);
  }

  @Test
  void refusesValuesTheEventHadWithoutAChangeThatSetThem() {
    givenEventAndMember();
    event.apply(plan(event.getState(), cancelled()));

    assertRefused(
        () -> service.publish(command(CANCELLATION)),
        RuleViolationException.class,
        "NOTHING_CHANGES");
  }

  @Test
  void refusesValuesTheEventHasWhenTheLatestChangeSetOthers() {
    givenEventAndMember();
    event.apply(plan(event.getState(), cancelled()));
    // Like a seeded change: its values are not the ones the event has now.
    EventChange unrelated =
        new EventChange(
            UUID.randomUUID(),
            event.getId(),
            ChangeType.CANCELLATION,
            Severity.CRITICAL,
            Map.of("status", "anterior"),
            Map.of("status", "ENDED"),
            null,
            null,
            null,
            SourceType.ORGANIZATION,
            "Ribeira Live",
            NOW,
            true);
    when(changes.findLatest(event.getId(), ChangeType.CANCELLATION))
        .thenReturn(Optional.of(unrelated));

    assertRefused(
        () -> service.publish(command(CANCELLATION)),
        RuleViolationException.class,
        "NOTHING_CHANGES");
    verify(changes, never()).save(any());
  }

  @Test
  void refusesAnEventThatDoesNotExist() {
    assertRefused(
        () -> service.publish(new PublishChangeCommand(UUID.randomUUID(), MEMBER, CANCELLATION)),
        NotFoundException.class,
        "EVENT_NOT_FOUND");
  }

  @Test
  void refusesAMemberOfAnotherOrganization() {
    when(events.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
    when(users.findById(MEMBER))
        .thenReturn(
            Optional.of(new User(MEMBER, UUID.randomUUID(), Role.ORGANIZATION_MEMBER, true)));

    assertRefused(
        () -> service.publish(command(CANCELLATION)),
        NotAllowedException.class,
        "NOT_EVENT_ORGANIZATION");
    verify(changes, never()).save(any());
    verify(audit, never()).record(any(), any(), anyString(), anyString(), any(), anyMap());
  }

  @Test
  void refusesADeactivatedMember() {
    when(events.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
    when(users.findById(MEMBER))
        .thenReturn(Optional.of(new User(MEMBER, ORGANIZATION, Role.ORGANIZATION_MEMBER, false)));

    assertRefused(
        () -> service.publish(command(CANCELLATION)),
        NotAllowedException.class,
        "NOT_EVENT_ORGANIZATION");
    verify(changes, never()).save(any());
  }

  @Test
  void refusesAParticipantAndAnUnknownUser() {
    when(events.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
    UUID participant = UUID.randomUUID();
    when(users.findById(participant))
        .thenReturn(Optional.of(new User(participant, null, Role.PARTICIPANT, true)));

    assertRefused(
        () -> service.publish(new PublishChangeCommand(event.getId(), participant, CANCELLATION)),
        NotAllowedException.class,
        "NOT_EVENT_ORGANIZATION");
    assertRefused(
        () ->
            service.publish(
                new PublishChangeCommand(event.getId(), UUID.randomUUID(), CANCELLATION)),
        NotAllowedException.class,
        "NOT_EVENT_ORGANIZATION");
  }

  @Test
  void failsWhenTheOrganizationIsMissing() {
    givenEventAndMember();
    when(organizations.findById(ORGANIZATION)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.publish(command(CANCELLATION)))
        .isInstanceOf(IllegalStateException.class);
  }

  private void givenEventAndMember() {
    when(events.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
    when(users.findById(MEMBER))
        .thenReturn(Optional.of(new User(MEMBER, ORGANIZATION, Role.ORGANIZATION_MEMBER, true)));
  }

  private PublishChangeCommand command(RequestedChange change) {
    return new PublishChangeCommand(event.getId(), MEMBER, change);
  }

  private EventChange cancellationOf(EventState before) {
    return EventChange.publish(event.getId(), plan(before, cancelled()), "Ribeira Live", NOW);
  }

  private static EventState cancelled() {
    return new EventState(
        EventStatus.CANCELLED,
        SCHEDULED.startDateTime(),
        null,
        null,
        SCHEDULED.venueName(),
        SCHEDULED.address(),
        null,
        null);
  }

  private static ChangePlan plan(EventState before, EventState after) {
    return new ChangePlan(
        ChangeType.CANCELLATION, Severity.CRITICAL, before, after, false, null, null);
  }

  private static void assertRefused(
      ThrowingCallable call, Class<? extends DomainException> type, String code) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(type, e -> assertThat(e.getCode()).isEqualTo(code));
  }
}
