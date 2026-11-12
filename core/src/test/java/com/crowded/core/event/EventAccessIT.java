package com.crowded.core.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crowded.core.IntegrationTest;
import com.crowded.core.event.application.service.EventAccess;
import com.crowded.core.shared.domain.exception.NotAllowedException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Who may see an event, against the demo data's connections (database/README.md): Ana and Carla
 * hold tickets for Festival Margens do Douro, Bruno does not; Ribeira Live owns it.
 */
class EventAccessIT extends IntegrationTest {

  private static final UUID MARGENS = UUID.fromString("30000000-0000-4000-8000-000000000001");
  private static final UUID ANA = UUID.fromString("10000000-0000-4000-8000-000000000011");
  private static final UUID BRUNO = UUID.fromString("10000000-0000-4000-8000-000000000012");
  private static final UUID RIBEIRA_MEMBER =
      UUID.fromString("10000000-0000-4000-8000-000000000021");
  private static final UUID OTHER_MEMBER = UUID.fromString("10000000-0000-4000-8000-000000000022");
  private static final UUID ADMIN = UUID.fromString("10000000-0000-4000-8000-000000000001");

  @Autowired private EventAccess access;

  @Test
  void showsTheEventToConnectedParticipantsAndToItsOrganization() {
    assertThat(access.viewable(ANA, MARGENS).getId()).isEqualTo(MARGENS);
    assertThat(access.viewable(RIBEIRA_MEMBER, MARGENS).getId()).isEqualTo(MARGENS);
  }

  @Test
  void hidesTheEventFromEveryoneElse() {
    for (UUID user : new UUID[] {BRUNO, OTHER_MEMBER, ADMIN}) {
      assertThatThrownBy(() -> access.viewable(user, MARGENS))
          .isInstanceOfSatisfying(
              NotAllowedException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_CONNECTED"));
    }
  }
}
