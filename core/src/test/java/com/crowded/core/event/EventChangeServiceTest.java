package com.crowded.core.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.crowded.core.audit.AuditLog;
import com.crowded.core.outbox.Outbox;
import com.crowded.core.user.AppUser;
import com.crowded.core.user.AppUserRepository;
import com.crowded.core.user.Organization;
import com.crowded.core.user.OrganizationRepository;
import com.crowded.core.user.Role;
import com.crowded.core.web.ApiException;
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
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

class EventChangeServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
  private static final UUID ORGANIZATION = UUID.randomUUID();
  private static final UUID MEMBER = UUID.randomUUID();
  private static final ChangeRequest CANCELLATION =
      new ChangeRequest(ChangeType.CANCELLATION, null, false, "Reembolso no site", null);

  private final EventRepository events = mock(EventRepository.class);
  private final EventChangeRepository changes = mock(EventChangeRepository.class);
  private final AppUserRepository users = mock(AppUserRepository.class);
  private final OrganizationRepository organizations = mock(OrganizationRepository.class);
  private final Outbox outbox = mock(Outbox.class);
  private final AuditLog audit = mock(AuditLog.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final EventChangeService service =
      new EventChangeService(
          events,
          changes,
          users,
          organizations,
          outbox,
          audit,
          JsonMapper.builder().build(),
          Clock.fixed(NOW, ZoneOffset.UTC),
          meters);

  private final Event event =
      new Event(
          UUID.randomUUID(),
          ORGANIZATION,
          new EventState(
              EventStatus.SCHEDULED,
              Instant.parse("2026-11-20T20:00:00Z"),
              null,
              null,
              "Coliseu",
              "Porto",
              null,
              null));

  @Test
  void recordsTheChangeUpdatesTheEventAndAsksForNotifications() {
    givenEventAndMember();
    when(organizations.findById(ORGANIZATION))
        .thenReturn(Optional.of(new Organization(ORGANIZATION, "Ribeira Live")));

    PublishedChange published = service.publish(event.getId(), MEMBER, CANCELLATION);

    ArgumentCaptor<EventChange> saved = ArgumentCaptor.forClass(EventChange.class);
    verify(changes).save(saved.capture());
    EventChange change = saved.getValue();
    assertThat(published).isEqualTo(new PublishedChange(change.getId(), Severity.CRITICAL, true));
    assertThat(change.getType()).isEqualTo(ChangeType.CANCELLATION);
    assertThat(change.getPreviousValue()).isEqualTo("{\"status\":\"SCHEDULED\"}");
    assertThat(change.getNewValue()).isEqualTo("{\"status\":\"CANCELLED\"}");
    assertThat(change.getTicketStillValid()).isFalse();
    assertThat(change.getRefundInfo()).isEqualTo("Reembolso no site");
    assertThat(change.getMessage()).isNull();
    assertThat(change.getProvenanceSourceName()).isEqualTo("Ribeira Live");
    assertThat(event.state().status()).isEqualTo(EventStatus.CANCELLED);
    verify(outbox).requestNotification("OFFICIAL_CHANGE", event.getId(), change.getId());
    verify(audit)
        .record(
            MEMBER,
            event.getId(),
            "EVENT_CHANGE_PUBLISHED",
            "EventChange",
            change.getId(),
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
    event.moveTo(withStatus(EventStatus.CANCELLED));
    EventChange first =
        new EventChange(
            event.getId(),
            new ChangePlan(
                ChangeType.CANCELLATION, Severity.CRITICAL, null, null, null, null, null),
            "{}",
            "{}",
            "Ribeira Live",
            NOW);
    when(changes.findFirstByEventIdAndTypeOrderByProvenanceLastUpdatedAtDesc(
            event.getId(), ChangeType.CANCELLATION))
        .thenReturn(Optional.of(first));

    PublishedChange published = service.publish(event.getId(), MEMBER, CANCELLATION);

    assertThat(published).isEqualTo(new PublishedChange(first.getId(), Severity.CRITICAL, false));
    verify(changes, never()).save(any());
    verifyNoInteractions(outbox, audit);
  }

  @Test
  void refusesValuesTheEventAlreadyHadWithoutAChange() {
    givenEventAndMember();
    event.moveTo(withStatus(EventStatus.CANCELLED));

    assertRefused(
        () -> service.publish(event.getId(), MEMBER, CANCELLATION),
        "NOTHING_CHANGES",
        HttpStatus.BAD_REQUEST);
  }

  @Test
  void refusesAnEventThatDoesNotExist() {
    UUID unknown = UUID.randomUUID();

    assertRefused(
        () -> service.publish(unknown, MEMBER, CANCELLATION),
        "EVENT_NOT_FOUND",
        HttpStatus.NOT_FOUND);
  }

  @Test
  void refusesAMemberOfAnotherOrganization() {
    when(events.findForUpdateById(event.getId())).thenReturn(Optional.of(event));
    when(users.findById(MEMBER))
        .thenReturn(Optional.of(new AppUser(MEMBER, UUID.randomUUID(), Role.ORGANIZATION_MEMBER)));

    assertRefused(
        () -> service.publish(event.getId(), MEMBER, CANCELLATION),
        "NOT_EVENT_ORGANIZATION",
        HttpStatus.FORBIDDEN);
    verify(changes, never()).save(any());
    verify(audit, never()).record(any(), any(), anyString(), anyString(), any(), anyMap());
  }

  @Test
  void refusesAParticipantAndAnUnknownUser() {
    when(events.findForUpdateById(event.getId())).thenReturn(Optional.of(event));
    UUID participant = UUID.randomUUID();
    when(users.findById(participant))
        .thenReturn(Optional.of(new AppUser(participant, null, Role.PARTICIPANT)));

    assertRefused(
        () -> service.publish(event.getId(), participant, CANCELLATION),
        "NOT_EVENT_ORGANIZATION",
        HttpStatus.FORBIDDEN);
    assertRefused(
        () -> service.publish(event.getId(), UUID.randomUUID(), CANCELLATION),
        "NOT_EVENT_ORGANIZATION",
        HttpStatus.FORBIDDEN);
  }

  @Test
  void failsWhenTheOrganizationIsMissing() {
    givenEventAndMember();
    when(organizations.findById(eq(ORGANIZATION))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.publish(event.getId(), MEMBER, CANCELLATION))
        .isInstanceOf(IllegalStateException.class);
  }

  private void givenEventAndMember() {
    when(events.findForUpdateById(event.getId())).thenReturn(Optional.of(event));
    when(users.findById(MEMBER))
        .thenReturn(Optional.of(new AppUser(MEMBER, ORGANIZATION, Role.ORGANIZATION_MEMBER)));
  }

  private EventState withStatus(EventStatus status) {
    EventState state = event.state();
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

  private static void assertRefused(ThrowingCallable call, String code, HttpStatus status) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getCode()).isEqualTo(code);
              assertThat(e.getStatusCode()).isEqualTo(status);
            });
  }
}
