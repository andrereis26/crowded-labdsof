package com.crowded.core.connection.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.crowded.core.connection.domain.model.EventConnection;
import com.crowded.core.connection.domain.model.JoinedConnection;
import com.crowded.core.connection.domain.repository.ConnectionRepository;
import com.crowded.core.event.domain.model.Admission;
import com.crowded.core.event.domain.model.Capacity;
import com.crowded.core.event.domain.model.ConfirmationRules;
import com.crowded.core.event.domain.model.ConfirmationSettings;
import com.crowded.core.event.domain.model.Coordinates;
import com.crowded.core.event.domain.model.District;
import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.EventCode;
import com.crowded.core.event.domain.model.EventProfile;
import com.crowded.core.event.domain.model.EventState;
import com.crowded.core.event.domain.model.EventStatus;
import com.crowded.core.event.domain.model.EventType;
import com.crowded.core.event.domain.model.QueuePoints;
import com.crowded.core.event.domain.model.Schedule;
import com.crowded.core.event.domain.model.Venue;
import com.crowded.core.event.domain.repository.EventRepository;
import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.shared.domain.exception.NotFoundException;
import com.crowded.core.user.domain.model.Role;
import com.crowded.core.user.domain.model.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class AccessCodeConnectionsTest {

  private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");
  private static final Instant START = NOW.plus(5, ChronoUnit.DAYS);
  private static final User PARTICIPANT =
      User.restore(UUID.randomUUID(), null, Role.PARTICIPANT, true);
  static final Event ARRAIAL =
      Event.restore(
          UUID.randomUUID(),
          UUID.randomUUID(),
          new EventProfile("Arraial", "Arraial popular."),
          new Admission(EventType.PUBLIC, new EventCode("SAOJOAO26")),
          new EventState(
              EventStatus.SCHEDULED,
              new Schedule(
                  START, START.minus(1, ChronoUnit.HOURS), START.plus(4, ChronoUnit.HOURS)),
              new Venue("Fontainhas", "Porto", new Coordinates(41.14, -8.6))),
          new District("Porto"),
          new Capacity(5000),
          QueuePoints.NONE,
          new ConfirmationSettings(Set.of(), ConfirmationRules.DEFAULT));

  private final EventRepository events = mock(EventRepository.class);
  private final ConnectionRepository connections = mock(ConnectionRepository.class);
  private final AccessCodeConnections joining = new AccessCodeConnections(events, connections);

  @Test
  void connectsTheParticipantToTheEventOfTheCode() {
    when(events.findUpcomingPublicByAccessCode(new EventCode("SAOJOAO26"), NOW))
        .thenReturn(Optional.of(ARRAIAL));

    JoinedConnection joined = joining.join(PARTICIPANT, " saojoao26 ", NOW);

    assertThat(joined.created()).isTrue();
    assertThat(joined.event()).isSameAs(ARRAIAL);
    assertThat(joined.connection().getUserId()).isEqualTo(PARTICIPANT.getId());
    assertThat(joined.connection().getEventId()).isEqualTo(ARRAIAL.getId());
    InOrder order = inOrder(connections);
    order.verify(connections).lockConnectionsOf(PARTICIPANT.getId());
    order.verify(connections).findByUserAndEvent(PARTICIPANT.getId(), ARRAIAL.getId());
    order.verify(connections).add(argThat(c -> c.equals(joined.connection())));
  }

  @Test
  void keepsTheExistingConnectionOfAParticipantAlreadyConnected() {
    when(events.findUpcomingPublicByAccessCode(new EventCode("SAOJOAO26"), NOW))
        .thenReturn(Optional.of(ARRAIAL));
    EventConnection existing =
        EventConnection.restore(
            UUID.randomUUID(), PARTICIPANT.getId(), ARRAIAL.getId(), NOW.minus(2, ChronoUnit.DAYS));
    when(connections.findByUserAndEvent(PARTICIPANT.getId(), ARRAIAL.getId()))
        .thenReturn(Optional.of(existing));

    JoinedConnection joined = joining.join(PARTICIPANT, "SAOJOAO26", NOW);

    assertThat(joined.created()).isFalse();
    assertThat(joined.connection()).isSameAs(existing);
    verify(connections, never()).add(any());
  }

  @Test
  void answersTheSameForACodeOfNoEventAndForOneNoEventCouldHave() {
    when(events.findUpcomingPublicByAccessCode(any(), any())).thenReturn(Optional.empty());

    for (String code : new String[] {"NOPE2026", "no such code", ""}) {
      assertThatThrownBy(() -> joining.join(PARTICIPANT, code, NOW))
          .isInstanceOfSatisfying(
              NotFoundException.class,
              e -> assertThat(e.getCode()).isEqualTo("ACCESS_CODE_NOT_FOUND"));
    }
    verifyNoInteractions(connections);
  }

  @Test
  void refusesAnyoneButAnActiveParticipantBeforeLookingAtTheCode() {
    User member =
        User.restore(UUID.randomUUID(), UUID.randomUUID(), Role.ORGANIZATION_MEMBER, true);
    User deactivated = User.restore(UUID.randomUUID(), null, Role.PARTICIPANT, false);

    for (User user : new User[] {member, deactivated}) {
      assertThatThrownBy(() -> joining.join(user, "SAOJOAO26", NOW))
          .isInstanceOfSatisfying(
              NotAllowedException.class,
              e -> assertThat(e.getCode()).isEqualTo("PARTICIPANT_ONLY"));
    }
    verifyNoInteractions(events, connections);
  }
}
