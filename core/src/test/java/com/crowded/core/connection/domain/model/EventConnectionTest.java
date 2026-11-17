package com.crowded.core.connection.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
    assertThat(connection.getConnectedAt()).isEqualTo(NOW);
  }

  @Test
  void connectsAParticipantToATicketedEventWithTheirTicket() {
    User participant = User.restore(UUID.randomUUID(), null, Role.PARTICIPANT, true);

    EventConnection connection = EventConnection.connectWithTicket(participant, EVENT, NOW);

    assertThat(connection.getId()).isNotNull();
    assertThat(connection.getUserId()).isEqualTo(participant.getId());
    assertThat(connection.getEventId()).isEqualTo(EVENT);
    assertThat(connection.getConnectedAt()).isEqualTo(NOW);
  }

  @Test
  void letsOnlyAnActiveParticipantConnect() {
    User participant = User.restore(UUID.randomUUID(), null, Role.PARTICIPANT, true);
    User member =
        User.restore(UUID.randomUUID(), UUID.randomUUID(), Role.ORGANIZATION_MEMBER, true);
    User deactivated = User.restore(UUID.randomUUID(), null, Role.PARTICIPANT, false);

    assertThatCode(() -> EventConnection.checkCanConnect(participant)).doesNotThrowAnyException();
    for (User user : new User[] {member, deactivated}) {
      assertThatThrownBy(() -> EventConnection.checkCanConnect(user))
          .isInstanceOfSatisfying(
              NotAllowedException.class,
              e -> assertThat(e.getCode()).isEqualTo("PARTICIPANT_ONLY"));
      assertThatThrownBy(() -> EventConnection.joinWithAccessCode(user, EVENT, NOW))
          .isInstanceOfSatisfying(
              NotAllowedException.class,
              e -> assertThat(e.getCode()).isEqualTo("PARTICIPANT_ONLY"));
      assertThatThrownBy(() -> EventConnection.connectWithTicket(user, EVENT, NOW))
          .isInstanceOfSatisfying(
              NotAllowedException.class,
              e -> assertThat(e.getCode()).isEqualTo("PARTICIPANT_ONLY"));
    }
  }

  @Test
  void isTheSameConnectionWhenItHasTheSameId() {
    UUID id = UUID.randomUUID();
    EventConnection stored = EventConnection.restore(id, UUID.randomUUID(), EVENT, NOW);
    EventConnection again = EventConnection.restore(id, UUID.randomUUID(), EVENT, NOW);

    assertThat(stored).isEqualTo(again).hasSameHashCodeAs(again);
    assertThat(stored)
        .isNotEqualTo(EventConnection.restore(UUID.randomUUID(), stored.getUserId(), EVENT, NOW));
    assertThat(stored.toString()).contains(id.toString());
  }
}
