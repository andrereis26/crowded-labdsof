package com.crowded.notifications.notification.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.crowded.notifications.notification.domain.model.Connection;
import com.crowded.notifications.notification.domain.model.Recipient;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecipientSelectorTest {

  private static final UUID ANA = UUID.randomUUID();
  private static final UUID BRUNO = UUID.randomUUID();
  private static final UUID CARLA = UUID.randomUUID();

  @Test
  void everyConnectedParticipantWhoWantsItIsNotifiedOnce() {
    List<Connection> connections =
        List.of(
            new Connection(ANA, false, null, null),
            new Connection(BRUNO, true, false, null),
            new Connection(ANA, true, null, null),
            new Connection(CARLA, false, null, true));

    assertThat(RecipientSelector.select(connections, List.of()))
        .containsExactly(new Recipient(ANA, true), new Recipient(CARLA, false));
  }

  @Test
  void aConnectionWithTheTypeTurnedOffDoesNotMakeATicketHolder() {
    List<Connection> connections =
        List.of(new Connection(ANA, true, false, null), new Connection(ANA, false, true, null));

    assertThat(RecipientSelector.select(connections, List.of()))
        .containsExactly(new Recipient(ANA, false));
  }

  @Test
  void onlyTheNamedUsersAreNotifiedWhenTheMessageNamesThem() {
    List<Connection> connections =
        List.of(new Connection(ANA, true, null, null), new Connection(BRUNO, true, null, null));

    assertThat(RecipientSelector.select(connections, List.of(BRUNO, CARLA)))
        .containsExactly(new Recipient(BRUNO, true));
  }
}
