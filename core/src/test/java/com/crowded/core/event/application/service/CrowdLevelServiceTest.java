package com.crowded.core.event.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.crowded.core.connection.domain.repository.ConnectionRepository;
import com.crowded.core.event.application.dto.PublishCrowdLevelCommand;
import com.crowded.core.event.domain.model.CrowdLevel;
import com.crowded.core.event.domain.model.CrowdStatus;
import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.EventState;
import com.crowded.core.event.domain.model.EventStatus;
import com.crowded.core.event.domain.model.Provenance;
import com.crowded.core.event.domain.model.Schedule;
import com.crowded.core.event.domain.model.StoredEvents;
import com.crowded.core.event.domain.model.Venue;
import com.crowded.core.event.domain.repository.EventRepository;
import com.crowded.core.event.domain.repository.EventUpdates;
import com.crowded.core.organization.domain.model.Organization;
import com.crowded.core.organization.domain.repository.OrganizationRepository;
import com.crowded.core.shared.domain.exception.ConflictException;
import com.crowded.core.shared.domain.exception.DomainException;
import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.shared.domain.exception.NotFoundException;
import com.crowded.core.user.domain.model.Role;
import com.crowded.core.user.domain.model.User;
import com.crowded.core.user.domain.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class CrowdLevelServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-20T21:00:00Z");
  private static final UUID ORGANIZATION = UUID.randomUUID();
  private static final UUID MEMBER = UUID.randomUUID();
  private static final EventState ONGOING =
      new EventState(
          EventStatus.ONGOING,
          new Schedule(NOW.minusSeconds(3600), null, NOW.plusSeconds(7200)),
          new Venue("Coliseu", "Porto", null));

  private final EventRepository events = mock(EventRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final OrganizationRepository organizations = mock(OrganizationRepository.class);
  private final EventUpdates updates = mock(EventUpdates.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final CrowdLevelService service =
      new CrowdLevelService(
          events,
          new EventAccess(events, users, mock(ConnectionRepository.class), meters),
          organizations,
          updates,
          Clock.fixed(NOW, ZoneOffset.UTC),
          meters);

  private Event event = StoredEvents.restore(UUID.randomUUID(), ORGANIZATION, ONGOING);

  @Test
  void publishesTheLevelFromTheOrganizationAndRefreshesThePage() {
    givenEventAndMember();

    CrowdStatus published = service.publish(command(CrowdLevel.HIGH));

    CrowdStatus expected =
        new CrowdStatus(CrowdLevel.HIGH, Provenance.organization("Ribeira Live", NOW));
    assertThat(published).isEqualTo(expected);
    assertThat(event.getCrowd()).isEqualTo(expected);
    verify(events).save(event);
    verify(updates).announce(event.getId());
    assertThat(meters.get("crowded.event.crowd.published").tag("level", "HIGH").counter().count())
        .isOne();
  }

  @Test
  void refusesAMemberOfAnotherOrganizationAndChangesNothing() {
    when(events.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
    when(users.findById(MEMBER))
        .thenReturn(
            Optional.of(User.restore(MEMBER, UUID.randomUUID(), Role.ORGANIZATION_MEMBER, true)));

    assertRefused(
        () -> service.publish(command(CrowdLevel.FULL)),
        NotAllowedException.class,
        "NOT_EVENT_ORGANIZATION");
    assertThat(event.getCrowd()).isNull();
    verify(events, never()).save(any());
    verifyNoInteractions(updates);
  }

  @Test
  void refusesAnEventThatDoesNotExist() {
    assertRefused(
        () ->
            service.publish(
                new PublishCrowdLevelCommand(UUID.randomUUID(), MEMBER, CrowdLevel.LOW)),
        NotFoundException.class,
        "EVENT_NOT_FOUND");
  }

  @Test
  void refusesAnEventThatIsOver() {
    event = StoredEvents.restore(event.getId(), ORGANIZATION, ONGOING.inStatus(EventStatus.ENDED));
    givenEventAndMember();

    assertRefused(
        () -> service.publish(command(CrowdLevel.LOW)), ConflictException.class, "EVENT_CLOSED");
    verify(events, never()).save(any());
    verifyNoInteractions(updates);
  }

  private void givenEventAndMember() {
    when(events.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
    when(organizations.findById(ORGANIZATION))
        .thenReturn(Optional.of(Organization.restore(ORGANIZATION, "Ribeira Live", null, NOW)));
    when(users.findById(MEMBER))
        .thenReturn(
            Optional.of(User.restore(MEMBER, ORGANIZATION, Role.ORGANIZATION_MEMBER, true)));
  }

  private PublishCrowdLevelCommand command(CrowdLevel level) {
    return new PublishCrowdLevelCommand(event.getId(), MEMBER, level);
  }

  private static void assertRefused(
      ThrowingCallable call, Class<? extends DomainException> type, String code) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(type, e -> assertThat(e.getCode()).isEqualTo(code));
  }
}
