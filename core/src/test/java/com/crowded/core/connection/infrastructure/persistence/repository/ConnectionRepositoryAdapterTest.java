package com.crowded.core.connection.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.crowded.core.connection.domain.model.EventConnection;
import com.crowded.core.connection.infrastructure.persistence.entity.AccessCodeConnectionEntity;
import com.crowded.core.connection.infrastructure.persistence.entity.EventConnectionEntity;
import com.crowded.core.connection.infrastructure.persistence.mapper.ConnectionPersistenceMapperImpl;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ConnectionRepositoryAdapterTest {

  private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");

  private final SpringDataEventConnectionRepository rows =
      mock(SpringDataEventConnectionRepository.class);
  private final ConnectionRepositoryAdapter adapter =
      new ConnectionRepositoryAdapter(rows, new ConnectionPersistenceMapperImpl());

  @Test
  void asksWhetherTheUserHasAConnectionToTheEvent() {
    UUID user = UUID.randomUUID();
    UUID event = UUID.randomUUID();
    when(rows.existsByUserIdAndEventId(user, event)).thenReturn(true);

    assertThat(adapter.isConnected(user, event)).isTrue();
    assertThat(adapter.isConnected(user, UUID.randomUUID())).isFalse();
  }

  @Test
  void countsTheConnectionsOfEachEvent() {
    UUID event = UUID.randomUUID();
    List<UUID> events = List.of(event, UUID.randomUUID());
    when(rows.countGroupedByEvent(events)).thenReturn(List.<Object[]>of(new Object[] {event, 3L}));

    assertThat(adapter.countByEvent(events)).isEqualTo(Map.of(event, 3L));
  }

  @Test
  void countsNothingForNoEvents() {
    assertThat(adapter.countByEvent(List.of())).isEmpty();
    verifyNoInteractions(rows);
  }

  @Test
  void locksTheParticipantsConnectionsByTheirId() {
    UUID user = UUID.randomUUID();

    adapter.lockConnectionsOf(user);

    verify(rows)
        .lockUntilCommit(ConnectionRepositoryAdapter.PARTICIPANT_CONNECTIONS_LOCK, user.toString());
  }

  @Test
  void storesAnAccessCodeConnectionInItsOwnTable() {
    EventConnection connection =
        EventConnection.restore(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), NOW);
    ArgumentCaptor<EventConnectionEntity> saved =
        ArgumentCaptor.forClass(EventConnectionEntity.class);

    adapter.add(connection);

    verify(rows).save(saved.capture());
    assertThat(saved.getValue()).isInstanceOf(AccessCodeConnectionEntity.class);
    // Inserted as new, so Spring Data persists it without reading it first.
    assertThat(saved.getValue().isNew()).isTrue();
    assertThat(saved.getValue().getId()).isEqualTo(connection.getId());
    assertThat(saved.getValue().getUserId()).isEqualTo(connection.getUserId());
    assertThat(saved.getValue().getEventId()).isEqualTo(connection.getEventId());
    assertThat(saved.getValue().getConnectedAt()).isEqualTo(NOW);
  }

  @Test
  void readsAStoredConnectionOfEitherKind() {
    UUID user = UUID.randomUUID();
    UUID publicEvent = UUID.randomUUID();
    UUID ticketedEvent = UUID.randomUUID();
    EventConnectionEntity accessCodeRow = row(new AccessCodeConnectionEntity(), user, publicEvent);
    when(rows.findFirstByUserIdAndEventIdOrderByConnectedAt(user, publicEvent))
        .thenReturn(Optional.of(accessCodeRow));
    when(rows.findFirstByUserIdAndEventIdOrderByConnectedAt(user, ticketedEvent))
        .thenReturn(Optional.of(row(new EventConnectionEntity(), user, ticketedEvent)));

    assertThat(adapter.findByUserAndEvent(user, publicEvent))
        .hasValueSatisfying(
            c -> {
              assertThat(c.getId()).isEqualTo(accessCodeRow.getId());
              assertThat(c.getUserId()).isEqualTo(user);
              assertThat(c.getEventId()).isEqualTo(publicEvent);
              assertThat(c.getConnectedAt()).isEqualTo(NOW);
            });
    assertThat(adapter.findByUserAndEvent(user, ticketedEvent))
        .hasValueSatisfying(c -> assertThat(c.getEventId()).isEqualTo(ticketedEvent));
    assertThat(adapter.findByUserAndEvent(user, UUID.randomUUID())).isEmpty();
    verify(rows, never()).save(any());
  }

  @Test
  void readsAConnectionById() {
    EventConnectionEntity ticketRow =
        row(new EventConnectionEntity(), UUID.randomUUID(), UUID.randomUUID());
    when(rows.findById(ticketRow.getId())).thenReturn(Optional.of(ticketRow));

    assertThat(adapter.findById(ticketRow.getId()))
        .hasValueSatisfying(c -> assertThat(c.getId()).isEqualTo(ticketRow.getId()));
    assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
  }

  private static EventConnectionEntity row(EventConnectionEntity row, UUID user, UUID event) {
    row.setId(UUID.randomUUID());
    row.setUserId(user);
    row.setEventId(event);
    row.setConnectedAt(NOW);
    return row;
  }
}
