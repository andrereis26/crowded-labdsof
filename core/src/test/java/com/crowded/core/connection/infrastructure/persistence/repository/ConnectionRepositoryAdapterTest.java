package com.crowded.core.connection.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConnectionRepositoryAdapterTest {

  @Test
  void asksWhetherTheUserHasAConnectionToTheEvent() {
    SpringDataEventConnectionRepository rows = mock(SpringDataEventConnectionRepository.class);
    UUID user = UUID.randomUUID();
    UUID event = UUID.randomUUID();
    when(rows.existsByUserIdAndEventId(user, event)).thenReturn(true);
    ConnectionRepositoryAdapter adapter = new ConnectionRepositoryAdapter(rows);

    assertThat(adapter.isConnected(user, event)).isTrue();
    assertThat(adapter.isConnected(user, UUID.randomUUID())).isFalse();
  }

  @Test
  void countsTheConnectionsOfEachEvent() {
    SpringDataEventConnectionRepository rows = mock(SpringDataEventConnectionRepository.class);
    UUID event = UUID.randomUUID();
    List<UUID> events = List.of(event, UUID.randomUUID());
    when(rows.countGroupedByEvent(events)).thenReturn(List.<Object[]>of(new Object[] {event, 3L}));
    ConnectionRepositoryAdapter adapter = new ConnectionRepositoryAdapter(rows);

    assertThat(adapter.countByEvent(events)).isEqualTo(Map.of(event, 3L));
  }

  @Test
  void countsNothingForNoEvents() {
    SpringDataEventConnectionRepository rows = mock(SpringDataEventConnectionRepository.class);

    assertThat(new ConnectionRepositoryAdapter(rows).countByEvent(List.of())).isEmpty();
    verifyNoInteractions(rows);
  }
}
