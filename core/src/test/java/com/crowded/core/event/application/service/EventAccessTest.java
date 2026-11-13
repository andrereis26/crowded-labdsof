package com.crowded.core.event.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crowded.core.connection.domain.repository.ConnectionRepository;
import com.crowded.core.event.domain.model.Coordinates;
import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.EventState;
import com.crowded.core.event.domain.model.EventStatus;
import com.crowded.core.event.domain.model.Schedule;
import com.crowded.core.event.domain.model.StoredEvents;
import com.crowded.core.event.domain.model.Venue;
import com.crowded.core.event.domain.repository.EventRepository;
import com.crowded.core.shared.domain.exception.DomainException;
import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.shared.domain.exception.NotFoundException;
import com.crowded.core.user.domain.model.Role;
import com.crowded.core.user.domain.model.User;
import com.crowded.core.user.domain.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventAccessTest {

  private static final UUID ORGANIZATION = UUID.randomUUID();
  private static final UUID PARTICIPANT = UUID.randomUUID();
  private static final UUID MEMBER = UUID.randomUUID();
  private static final Event EVENT =
      StoredEvents.restore(
          UUID.randomUUID(),
          ORGANIZATION,
          new EventState(
              EventStatus.SCHEDULED,
              new Schedule(
                  Instant.parse("2026-11-20T20:00:00Z"),
                  null,
                  Instant.parse("2026-11-20T23:00:00Z")),
              new Venue("Coliseu", "Porto", new Coordinates(41.146, -8.606))));

  private final EventRepository events = mock(EventRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final ConnectionRepository connections = mock(ConnectionRepository.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final EventAccess access = new EventAccess(events, users, connections, meters);

  @BeforeEach
  void givenTheEventAndItsUsers() {
    when(events.findById(EVENT.getId())).thenReturn(Optional.of(EVENT));
    when(users.findById(PARTICIPANT))
        .thenReturn(Optional.of(User.restore(PARTICIPANT, null, Role.PARTICIPANT, true)));
    when(users.findById(MEMBER))
        .thenReturn(
            Optional.of(User.restore(MEMBER, ORGANIZATION, Role.ORGANIZATION_MEMBER, true)));
  }

  @Test
  void letsAConnectedParticipantSeeTheEvent() {
    when(connections.isConnected(PARTICIPANT, EVENT.getId())).thenReturn(true);

    assertThat(access.viewable(PARTICIPANT, EVENT.getId())).isEqualTo(EVENT);
    assertThat(access.viewable(MEMBER, EVENT.getId())).isEqualTo(EVENT);
  }

  @Test
  void refusesAParticipantWithoutAConnectionAndCountsIt() {
    assertRefused(
        () -> access.viewable(PARTICIPANT, EVENT.getId()),
        NotAllowedException.class,
        "NOT_CONNECTED");
    assertRefused(
        () -> access.viewable(UUID.randomUUID(), EVENT.getId()),
        NotAllowedException.class,
        "NOT_CONNECTED");
    assertThat(meters.counter("crowded.access.refused", "access", "view").count()).isEqualTo(2);
  }

  @Test
  void saysWhenTheEventDoesNotExist() {
    assertRefused(
        () -> access.viewable(PARTICIPANT, UUID.randomUUID()),
        NotFoundException.class,
        "EVENT_NOT_FOUND");
  }

  @Test
  void letsOnlyAMemberOfTheOrganizationManageTheEvent() {
    access.checkManager(MEMBER, EVENT);

    assertRefused(
        () -> access.checkManager(PARTICIPANT, EVENT),
        NotAllowedException.class,
        "NOT_EVENT_ORGANIZATION");
    assertRefused(
        () -> access.checkManager(UUID.randomUUID(), EVENT),
        NotAllowedException.class,
        "NOT_EVENT_ORGANIZATION");
    assertThat(meters.counter("crowded.access.refused", "access", "manage").count()).isEqualTo(2);
  }

  private static void assertRefused(
      ThrowingCallable call, Class<? extends DomainException> type, String code) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(type, e -> assertThat(e.getCode()).isEqualTo(code));
  }
}
