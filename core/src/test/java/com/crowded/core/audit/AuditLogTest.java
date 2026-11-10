package com.crowded.core.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class AuditLogTest {

  @Test
  void recordsWhoDidWhatWithItsDetailsAsJson() {
    AuditLogEntryRepository entries = mock(AuditLogEntryRepository.class);
    AuditLog audit =
        new AuditLog(
            entries, JsonMapper.builder().build(), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    UUID actor = UUID.randomUUID();
    UUID change = UUID.randomUUID();

    audit.record(
        actor,
        UUID.randomUUID(),
        "EVENT_CHANGE_PUBLISHED",
        "EventChange",
        change,
        Map.of("type", "CANCELLATION"));

    ArgumentCaptor<AuditLogEntry> saved = ArgumentCaptor.forClass(AuditLogEntry.class);
    verify(entries).save(saved.capture());
    assertThat(saved.getValue().getActorId()).isEqualTo(actor);
    assertThat(saved.getValue().getAction()).isEqualTo("EVENT_CHANGE_PUBLISHED");
    assertThat(saved.getValue().getEntityId()).isEqualTo(change);
    assertThat(saved.getValue().getDetails()).isEqualTo("{\"type\":\"CANCELLATION\"}");
  }
}
