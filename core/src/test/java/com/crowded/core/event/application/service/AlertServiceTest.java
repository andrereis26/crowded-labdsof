package com.crowded.core.event.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.crowded.core.audit.application.service.AuditLog;
import com.crowded.core.connection.domain.repository.ConnectionRepository;
import com.crowded.core.event.application.dto.SendAlertCommand;
import com.crowded.core.event.domain.model.Alert;
import com.crowded.core.event.domain.model.AlertType;
import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.EventState;
import com.crowded.core.event.domain.model.EventStatus;
import com.crowded.core.event.domain.model.Schedule;
import com.crowded.core.event.domain.model.Severity;
import com.crowded.core.event.domain.model.StoredEvents;
import com.crowded.core.event.domain.model.Venue;
import com.crowded.core.event.domain.repository.AlertRepository;
import com.crowded.core.event.domain.repository.EventRepository;
import com.crowded.core.event.domain.repository.EventUpdates;
import com.crowded.core.outbox.application.service.NotificationRequester;
import com.crowded.core.shared.domain.exception.ConflictException;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class AlertServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-20T21:00:00Z");
  private static final UUID ORGANIZATION = UUID.randomUUID();
  private static final UUID MEMBER = UUID.randomUUID();
  private static final EventState ONGOING =
      new EventState(
          EventStatus.ONGOING,
          new Schedule(NOW.minusSeconds(3600), null, NOW.plusSeconds(7200)),
          new Venue("Coliseu", "Porto", null));

  private final EventRepository events = mock(EventRepository.class);
  private final AlertRepository alerts = mock(AlertRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final ConnectionRepository connections = mock(ConnectionRepository.class);
  private final NotificationRequester notifications = mock(NotificationRequester.class);
  private final EventUpdates updates = mock(EventUpdates.class);
  private final AuditLog audit = mock(AuditLog.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final AlertService service =
      new AlertService(
          events,
          alerts,
          new EventAccess(events, users, connections, meters),
          connections,
          notifications,
          updates,
          audit,
          Clock.fixed(NOW, ZoneOffset.UTC),
          meters);

  private Event event = StoredEvents.restore(UUID.randomUUID(), ORGANIZATION, ONGOING);

  @Test
  void sendsTheAlertToTheConnectedParticipantsAuditsItAndRefreshesThePage() {
    givenEventAndMember();
    when(connections.countByEvent(List.of(event.getId()))).thenReturn(Map.of(event.getId(), 3L));

    Alert alert = service.send(command(AlertType.ENTRANCE_CLOSURE, "Porta A fechada."));

    assertThat(alert.getEventId()).isEqualTo(event.getId());
    assertThat(alert.getSeverity()).isEqualTo(Severity.CRITICAL);
    assertThat(alert.getMessage()).isEqualTo("Porta A fechada.");
    assertThat(alert.getSentAt()).isEqualTo(NOW);
    assertThat(alert.getRecipients()).isEqualTo(3);
    verify(alerts).save(alert);
    verify(notifications).request("OPERATIONAL_ALERT", event.getId(), alert.getId());
    verify(updates).announce(event.getId());
    verify(audit)
        .record(
            MEMBER,
            event.getId(),
            "ALERT_SENT",
            "Alert",
            alert.getId(),
            Map.of("type", "ENTRANCE_CLOSURE", "severity", "CRITICAL", "recipients", 3));
    assertThat(
            meters
                .get("crowded.alerts.sent")
                .tag("type", "ENTRANCE_CLOSURE")
                .tag("severity", "CRITICAL")
                .counter()
                .count())
        .isOne();
  }

  @Test
  void sendsToNobodyWhenNoParticipantIsConnected() {
    givenEventAndMember();

    assertThat(service.send(command(AlertType.OTHER, "Olá.")).getRecipients()).isZero();
  }

  @Test
  void refusesAMemberOfAnotherOrganizationAndSendsNothing() {
    when(events.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
    when(users.findById(MEMBER))
        .thenReturn(
            Optional.of(User.restore(MEMBER, UUID.randomUUID(), Role.ORGANIZATION_MEMBER, true)));

    assertRefused(
        () -> service.send(command(AlertType.OTHER, "Olá.")),
        NotAllowedException.class,
        "NOT_EVENT_ORGANIZATION");
    assertNothingSent();
  }

  @Test
  void refusesAnEventThatDoesNotExist() {
    assertRefused(
        () ->
            service.send(new SendAlertCommand(UUID.randomUUID(), MEMBER, AlertType.OTHER, "Olá.")),
        NotFoundException.class,
        "EVENT_NOT_FOUND");
    assertNothingSent();
  }

  @Test
  void refusesAnAlertWithoutAMessageOrAboutAnEventThatIsOver() {
    givenEventAndMember();

    assertRefused(
        () -> service.send(command(AlertType.OTHER, " ")),
        RuleViolationException.class,
        "ALERT_MESSAGE_REQUIRED");
    event = StoredEvents.restore(event.getId(), ORGANIZATION, ONGOING.inStatus(EventStatus.ENDED));
    givenEventAndMember();
    assertRefused(
        () -> service.send(command(AlertType.OTHER, "Olá.")),
        ConflictException.class,
        "EVENT_CLOSED");
    assertNothingSent();
  }

  private void givenEventAndMember() {
    when(events.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
    when(users.findById(MEMBER))
        .thenReturn(
            Optional.of(User.restore(MEMBER, ORGANIZATION, Role.ORGANIZATION_MEMBER, true)));
  }

  private SendAlertCommand command(AlertType type, String message) {
    return new SendAlertCommand(event.getId(), MEMBER, type, message);
  }

  private void assertNothingSent() {
    verify(alerts, never()).save(any());
    verifyNoInteractions(notifications, updates, audit);
  }

  private static void assertRefused(
      ThrowingCallable call, Class<? extends DomainException> type, String code) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(type, e -> assertThat(e.getCode()).isEqualTo(code));
  }
}
