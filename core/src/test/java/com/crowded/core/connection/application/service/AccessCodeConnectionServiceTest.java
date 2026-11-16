package com.crowded.core.connection.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.crowded.core.connection.application.dto.JoinWithAccessCodeCommand;
import com.crowded.core.connection.application.dto.JoinedEvent;
import com.crowded.core.connection.domain.model.EventConnection;
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
import com.crowded.core.user.domain.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessCodeConnectionServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");
  private static final UUID PARTICIPANT = UUID.randomUUID();
  private static final Instant START = NOW.plus(5, ChronoUnit.DAYS);
  private static final Event ARRAIAL =
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
  private final UserRepository users = mock(UserRepository.class);
  private final ConnectionRepository connections = mock(ConnectionRepository.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final AccessCodeConnectionService service =
      new AccessCodeConnectionService(
          events, users, connections, Clock.fixed(NOW, ZoneOffset.UTC), meters);

  @Test
  void answersTheNewConnectionWithItsEventAndCountsIt() {
    participant();
    when(events.findUpcomingPublicByAccessCode(new EventCode("SAOJOAO26"), NOW))
        .thenReturn(Optional.of(ARRAIAL));

    JoinedEvent joined = service.joinWithAccessCode(command("saojoao26"));

    assertThat(joined.created()).isTrue();
    assertThat(joined.connectionId()).isNotNull();
    assertThat(joined.eventId()).isEqualTo(ARRAIAL.getId());
    assertThat(joined.name()).isEqualTo("Arraial");
    assertThat(joined.startDateTime()).isEqualTo(START);
    assertThat(joined.doorsOpenAt()).isEqualTo(START.minus(1, ChronoUnit.HOURS));
    assertThat(joined.venueName()).isEqualTo("Fontainhas");
    assertThat(joins("CONNECTED")).isOne();
  }

  @Test
  void answersTheExistingConnectionAndCountsIt() {
    participant();
    when(events.findUpcomingPublicByAccessCode(new EventCode("SAOJOAO26"), NOW))
        .thenReturn(Optional.of(ARRAIAL));
    EventConnection existing =
        EventConnection.restore(UUID.randomUUID(), PARTICIPANT, ARRAIAL.getId(), NOW);
    when(connections.findByUserAndEvent(PARTICIPANT, ARRAIAL.getId()))
        .thenReturn(Optional.of(existing));

    JoinedEvent joined = service.joinWithAccessCode(command("SAOJOAO26"));

    assertThat(joined.created()).isFalse();
    assertThat(joined.connectionId()).isEqualTo(existing.getId());
    assertThat(joins("ALREADY_CONNECTED")).isOne();
  }

  @Test
  void countsACodeOfNoEvent() {
    participant();
    when(events.findUpcomingPublicByAccessCode(any(), any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.joinWithAccessCode(command("NOPE2026")))
        .isInstanceOf(NotFoundException.class);
    assertThat(joins("NOT_FOUND")).isOne();
  }

  @Test
  void refusesAnUnknownUser() {
    assertThatThrownBy(() -> service.joinWithAccessCode(command("SAOJOAO26")))
        .isInstanceOfSatisfying(
            NotAllowedException.class, e -> assertThat(e.getCode()).isEqualTo("PARTICIPANT_ONLY"));
    verifyNoInteractions(events, connections);
  }

  private void participant() {
    when(users.findById(PARTICIPANT))
        .thenReturn(Optional.of(User.restore(PARTICIPANT, null, Role.PARTICIPANT, true)));
  }

  private static JoinWithAccessCodeCommand command(String code) {
    return new JoinWithAccessCodeCommand(PARTICIPANT, code);
  }

  private double joins(String outcome) {
    return meters.get("crowded.connections.join").tag("outcome", outcome).counter().count();
  }
}
