package com.crowded.core.audit.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.crowded.core.audit.domain.model.AuditLogEntry;
import com.crowded.core.audit.domain.repository.AuditLogRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuditLogTest {

  @Test
  void recordsWhoDidWhatAndWhen() {
    AuditLogRepository entries = mock(AuditLogRepository.class);
    UUID actor = UUID.randomUUID();
    UUID event = UUID.randomUUID();
    UUID change = UUID.randomUUID();

    new AuditLog(entries, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
        .record(
            actor,
            event,
            "EVENT_CHANGE_PUBLISHED",
            "EventChange",
            change,
            Map.of("type", "CANCELLATION"));

    ArgumentCaptor<AuditLogEntry> saved = ArgumentCaptor.forClass(AuditLogEntry.class);
    verify(entries).save(saved.capture());
    assertThat(saved.getValue())
        .usingRecursiveComparison()
        .ignoringFields("id")
        .isEqualTo(
            AuditLogEntry.record(
                actor,
                event,
                "EVENT_CHANGE_PUBLISHED",
                "EventChange",
                change,
                Map.of("type", "CANCELLATION"),
                Instant.EPOCH));
    assertThat(saved.getValue().getId()).isNotNull();
  }
}
