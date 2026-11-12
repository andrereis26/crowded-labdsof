package com.crowded.core.outbox.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crowded.core.outbox.domain.model.NotificationRequested;
import com.crowded.core.outbox.domain.model.OutboxMessage;
import com.crowded.core.outbox.infrastructure.persistence.entity.OutboxMessageEntity;
import com.crowded.core.outbox.infrastructure.persistence.mapper.OutboxMessagePersistenceMapperImpl;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class OutboxRepositoryAdapterTest {

  private final SpringDataOutboxRepository rows = mock(SpringDataOutboxRepository.class);
  private final JsonMapper json = JsonMapper.builder().build();
  private final OutboxRepositoryAdapter outbox =
      new OutboxRepositoryAdapter(
          rows,
          new OutboxMessagePersistenceMapperImpl(),
          json,
          Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

  @Test
  void storesTheMessageAsJsonWithTheContractOfTheMessage() {
    NotificationRequested message =
        new NotificationRequested(
            UUID.randomUUID(), "OFFICIAL_CHANGE", UUID.randomUUID(), UUID.randomUUID(), List.of());

    outbox.add(message);

    ArgumentCaptor<OutboxMessageEntity> saved = ArgumentCaptor.forClass(OutboxMessageEntity.class);
    verify(rows).save(saved.capture());
    assertThat(saved.getValue().getId()).isEqualTo(message.messageId());
    assertThat(saved.getValue().getRoutingKey()).isEqualTo("notification.requested");
    assertThat(saved.getValue().getCreatedAt()).isEqualTo(Instant.EPOCH);
    assertThat(json.readTree(saved.getValue().getPayload()).propertyNames())
        .containsExactlyInAnyOrder("messageId", "type", "eventId", "causeId", "userIds");
    assertThat(json.readValue(saved.getValue().getPayload(), NotificationRequested.class))
        .isEqualTo(message);
  }

  @Test
  void readsTheOldestWaitingMessagesAndRemovesOne() {
    OutboxMessageEntity row = new OutboxMessageEntity();
    row.setId(UUID.randomUUID());
    row.setRoutingKey("notification.requested");
    row.setPayload("{}");
    row.setCreatedAt(Instant.EPOCH);
    when(rows.lockOldest(10)).thenReturn(List.of(row));

    assertThat(outbox.lockOldest(10))
        .singleElement()
        .usingRecursiveComparison()
        .isEqualTo(
            OutboxMessage.restore(row.getId(), "notification.requested", "{}", Instant.EPOCH));

    outbox.remove(row.getId());

    verify(rows).deleteById(row.getId());
  }
}
