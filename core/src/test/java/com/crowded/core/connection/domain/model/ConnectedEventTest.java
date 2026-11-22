package com.crowded.core.connection.domain.model;

import static com.crowded.core.connection.ConnectionTestData.NOW;
import static com.crowded.core.connection.ConnectionTestData.connected;
import static com.crowded.core.connection.ConnectionTestData.connectionTo;
import static com.crowded.core.connection.ConnectionTestData.eventStartingIn;
import static com.crowded.core.connection.ConnectionTestData.ticketOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.Severity;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConnectedEventTest {

  private static final UUID USER = UUID.randomUUID();

  @Test
  void aConnectionWithATicketWasMadeWithIt() {
    Event event = eventStartingIn(24);
    EventConnection connection = connectionTo(event, USER);

    ConnectedEvent withTicket =
        new ConnectedEvent(connection, event, null, List.of(), ticketOf(connection));
    ConnectedEvent withCode = new ConnectedEvent(connection, event, null, List.of(), null);

    assertThat(withTicket.type()).isEqualTo(ConnectionType.TICKET);
    assertThat(withCode.type()).isEqualTo(ConnectionType.ACCESS_CODE);
  }

  @Test
  void anEventIsOverOnceItEnds() {
    ConnectedEvent running = connected(eventStartingIn(-2));
    ConnectedEvent ended = connected(eventStartingIn(-4));

    assertThat(running.isOver(NOW)).isFalse();
    assertThat(ended.isOver(NOW)).isTrue();
    assertThat(ended.isOver(NOW.minus(5, ChronoUnit.HOURS))).isFalse();
  }

  @Test
  void anEventStartsWhenItsScheduleSays() {
    Event event = eventStartingIn(5);

    assertThat(connected(event).startsAt()).isEqualTo(NOW.plus(5, ChronoUnit.HOURS));
  }

  @Test
  void keepsItsAlertsAsACopy() {
    Event event = eventStartingIn(5);
    List<ActiveAlert> alerts = new ArrayList<>();

    ConnectedEvent connected =
        new ConnectedEvent(connectionTo(event, USER), event, null, alerts, null);
    alerts.add(
        new ActiveAlert(UUID.randomUUID(), "OTHER", Severity.INFO, "Aviso.", NOW, "Ribeira Live"));

    assertThat(connected.activeAlerts()).isEmpty();
    assertThatThrownBy(() -> connected.activeAlerts().add(null))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void anAlertNeedsEveryField() {
    assertThatThrownBy(
            () ->
                new ActiveAlert(
                    UUID.randomUUID(), "OTHER", Severity.INFO, null, NOW, "Ribeira Live"))
        .isInstanceOf(NullPointerException.class);
  }
}
