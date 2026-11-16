package com.crowded.core.connection.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.user.domain.model.Role;
import com.crowded.core.user.domain.model.User;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventConnectionTest {

  private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");
  private static final UUID EVENT = UUID.randomUUID();

  @Test
  void connectsAParticipantToAPublicEventByItsAccessCode() {
    User participant = User.restore(UUID.randomUUID(), null, Role.PARTICIPANT, true);

    EventConnection connection = EventConnection.joinWithAccessCode(participant, EVENT, NOW);

    assertThat(connection.getId()).isNotNull();
    assertThat(connection.getUserId()).isEqualTo(participant.getId());
    assertThat(connection.getEventId()).isEqualTo(EVENT);
    assertThat(connection.getType()).isEqualTo(ConnectionType.ACCESS_CODE);
    assertThat(connection.getConnectedAt()).isEqualTo(NOW);
  }

  @Test
  void refusesAnyoneButAnActiveParticipant() {
    User member =
        User.restore(UUID.randomUUID(), UUID.randomUUID(), Role.ORGANIZATION_MEMBER, true);
    User deactivated = User.restore(UUID.randomUUID(), null, Role.PARTICIPANT, false);

    for (User user : new User[] {member, deactivated}) {
      assertThatThrownBy(() -> EventConnection.joinWithAccessCode(user, EVENT, NOW))
          .isInstanceOfSatisfying(
              NotAllowedException.class,
              e -> assertThat(e.getCode()).isEqualTo("PARTICIPANT_ONLY"));
    }
  }

  @Test
  void isTheSameConnectionWhenItHasTheSameId() {
    UUID id = UUID.randomUUID();
    EventConnection stored =
        EventConnection.restore(id, UUID.randomUUID(), EVENT, ConnectionType.TICKET, NOW);
    EventConnection again =
        EventConnection.restore(id, UUID.randomUUID(), EVENT, ConnectionType.TICKET, NOW);

    assertThat(stored).isEqualTo(again).hasSameHashCodeAs(again);
    assertThat(stored)
        .isNotEqualTo(
            EventConnection.restore(
                UUID.randomUUID(), stored.getUserId(), EVENT, ConnectionType.TICKET, NOW));
    assertThat(stored.toString()).contains(id.toString());
  }
}
