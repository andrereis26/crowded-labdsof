package com.crowded.core.connection.domain.service;

import static com.crowded.core.connection.ConnectionTestData.NOW;
import static com.crowded.core.connection.ConnectionTestData.connected;
import static com.crowded.core.connection.ConnectionTestData.eventStartingIn;
import static org.assertj.core.api.Assertions.assertThat;

import com.crowded.core.connection.domain.model.ConnectedEvent;
import com.crowded.core.event.domain.model.EventStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConnectedEventOrderTest {

  @Test
  void putsEventsNotOverFirstTheSoonestFirstThenTheOnesOverTheLatestFirst() {
    ConnectedEvent later = connected(eventStartingIn(48));
    ConnectedEvent running = connected(eventStartingIn(-1));
    ConnectedEvent soon = connected(eventStartingIn(2));
    ConnectedEvent lastWeek = connected(eventStartingIn(-170));
    ConnectedEvent yesterday = connected(eventStartingIn(-24));

    List<ConnectedEvent> sorted =
        ConnectedEventOrder.sort(List.of(lastWeek, later, yesterday, soon, running), NOW);

    assertThat(sorted).containsExactly(running, soon, later, yesterday, lastWeek);
  }

  @Test
  void keepsACancelledEventThatHasNotEndedAmongThoseToCome() {
    ConnectedEvent cancelled = connected(eventStartingIn(3, EventStatus.CANCELLED));
    ConnectedEvent sooner = connected(eventStartingIn(1));
    ConnectedEvent over = connected(eventStartingIn(-24));

    assertThat(ConnectedEventOrder.sort(List.of(over, cancelled, sooner), NOW))
        .containsExactly(sooner, cancelled, over);
  }

  @Test
  void anEmptyListStaysEmpty() {
    assertThat(ConnectedEventOrder.sort(List.of(), NOW)).isEmpty();
  }
}
