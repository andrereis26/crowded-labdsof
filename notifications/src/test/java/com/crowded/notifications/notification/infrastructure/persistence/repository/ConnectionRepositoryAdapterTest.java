package com.crowded.notifications.notification.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crowded.notifications.notification.domain.model.Connection;
import com.crowded.notifications.notification.domain.model.NotificationType;
import com.crowded.notifications.notification.infrastructure.persistence.mapper.ConnectionPersistenceMapperImpl;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConnectionRepositoryAdapterTest {

  private final SpringDataConnectionRepository rows = mock(SpringDataConnectionRepository.class);
  private final ConnectionRepositoryAdapter connections =
      new ConnectionRepositoryAdapter(rows, new ConnectionPersistenceMapperImpl());

  @Test
  void readsTheConnectionsWithTheSettingsOfTheType() {
    UUID eventId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    ConnectionRow row = mock(ConnectionRow.class);
    when(row.getUserId()).thenReturn(userId);
    when(row.getTicket()).thenReturn(true);
    when(row.getEventSetting()).thenReturn(null);
    when(row.getOverallSetting()).thenReturn(false);
    when(rows.findByEvent(eventId, "OFFICIAL_CHANGE")).thenReturn(List.of(row));

    assertThat(connections.findByEvent(eventId, NotificationType.OFFICIAL_CHANGE))
        .containsExactly(new Connection(userId, true, null, false));
  }

  @Test
  void findsNothingForAnEventWithoutConnections() {
    assertThat(connections.findByEvent(UUID.randomUUID(), NotificationType.OFFICIAL_CHANGE))
        .isEmpty();
  }
}
