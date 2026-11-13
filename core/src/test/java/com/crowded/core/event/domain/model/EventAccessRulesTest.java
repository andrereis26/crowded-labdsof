package com.crowded.core.event.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.user.domain.model.Role;
import com.crowded.core.user.domain.model.User;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Who may see and who may manage an event. */
class EventAccessRulesTest {

  private static final UUID ORGANIZATION = UUID.randomUUID();
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

  private static final User MEMBER = user(Role.ORGANIZATION_MEMBER, ORGANIZATION, true);
  private static final User DEACTIVATED_MEMBER =
      user(Role.ORGANIZATION_MEMBER, ORGANIZATION, false);
  private static final User OUTSIDER = user(Role.ORGANIZATION_MEMBER, UUID.randomUUID(), true);
  private static final User PARTICIPANT = user(Role.PARTICIPANT, null, true);
  private static final User ADMINISTRATOR = user(Role.ADMINISTRATOR, null, true);

  @Test
  void showsTheEventToItsMembersAndToConnectedParticipants() {
    assertThatCode(() -> EVENT.checkCanBeSeenBy(MEMBER, false)).doesNotThrowAnyException();
    assertThatCode(() -> EVENT.checkCanBeSeenBy(PARTICIPANT, true)).doesNotThrowAnyException();
  }

  @Test
  void hidesTheEventFromAnyoneElse() {
    assertNotConnected(PARTICIPANT, false);
    assertNotConnected(OUTSIDER, false);
    assertNotConnected(DEACTIVATED_MEMBER, false);
    assertNotConnected(ADMINISTRATOR, false);
    // A connection counts for a participant only, not for another role.
    assertNotConnected(OUTSIDER, true);
    assertNotConnected(ADMINISTRATOR, true);
  }

  @Test
  void letsOnlyActiveMembersOfItsOrganizationManageIt() {
    assertThatCode(() -> EVENT.checkCanBeManagedBy(MEMBER)).doesNotThrowAnyException();
    for (User other : new User[] {DEACTIVATED_MEMBER, OUTSIDER, PARTICIPANT, ADMINISTRATOR}) {
      assertThatThrownBy(() -> EVENT.checkCanBeManagedBy(other))
          .isInstanceOfSatisfying(
              NotAllowedException.class,
              e -> assertThat(e.getCode()).isEqualTo("NOT_EVENT_ORGANIZATION"));
    }
  }

  private static void assertNotConnected(User user, boolean connected) {
    assertThatThrownBy(() -> EVENT.checkCanBeSeenBy(user, connected))
        .isInstanceOfSatisfying(
            NotAllowedException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_CONNECTED"));
  }

  private static User user(Role role, UUID organization, boolean active) {
    return User.restore(UUID.randomUUID(), organization, role, active);
  }
}
