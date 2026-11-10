package com.crowded.core.audit.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.crowded.core.audit.domain.model.AuditLogEntry;
import com.crowded.core.audit.infrastructure.persistence.entity.AuditLogEntryEntity;
import com.crowded.core.audit.infrastructure.persistence.mapper.AuditLogEntryPersistenceMapperImpl;
import com.crowded.core.shared.infrastructure.persistence.JsonColumnConverter;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class AuditLogRepositoryAdapterTest {

  @Test
  void storesTheEntryWithItsDetailsAsJson() {
    SpringDataAuditLogRepository rows = mock(SpringDataAuditLogRepository.class);
    AuditLogEntry entry =
        new AuditLogEntry(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "EVENT_CHANGE_PUBLISHED",
            "EventChange",
            UUID.randomUUID(),
            Map.of("type", "CANCELLATION"),
            Instant.EPOCH);

    new AuditLogRepositoryAdapter(
            rows,
            new AuditLogEntryPersistenceMapperImpl(
                new JsonColumnConverter(JsonMapper.builder().build())))
        .save(entry);

    ArgumentCaptor<AuditLogEntryEntity> saved = ArgumentCaptor.forClass(AuditLogEntryEntity.class);
    verify(rows).save(saved.capture());
    AuditLogEntryEntity row = saved.getValue();
    assertThat(row.getId()).isEqualTo(entry.id());
    assertThat(row.getActorId()).isEqualTo(entry.actorId());
    assertThat(row.getEventId()).isEqualTo(entry.eventId());
    assertThat(row.getAction()).isEqualTo("EVENT_CHANGE_PUBLISHED");
    assertThat(row.getEntityType()).isEqualTo("EventChange");
    assertThat(row.getEntityId()).isEqualTo(entry.entityId());
    assertThat(row.getDetails()).isEqualTo("{\"type\":\"CANCELLATION\"}");
    assertThat(row.getOccurredAt()).isEqualTo(Instant.EPOCH);
  }
}
