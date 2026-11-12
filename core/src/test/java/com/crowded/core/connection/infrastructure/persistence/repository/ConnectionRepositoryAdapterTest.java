package com.crowded.core.connection.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
}
