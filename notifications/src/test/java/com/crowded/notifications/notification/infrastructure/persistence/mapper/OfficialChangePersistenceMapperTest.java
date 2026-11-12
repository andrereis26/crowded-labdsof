package com.crowded.notifications.notification.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class OfficialChangePersistenceMapperTest {

  private final OfficialChangePersistenceMapper mapper = new OfficialChangePersistenceMapperImpl();

  @Test
  void readsTheInstantsCoreWrites() {
    assertThat(mapper.toInstant("2026-11-08T21:30:00Z"))
        .isEqualTo(Instant.parse("2026-11-08T21:30:00Z"));
  }

  @Test
  void leavesOutWhatIsMissingOrNotAnInstant() {
    assertThat(mapper.toInstant(null)).isNull();
    assertThat(mapper.toInstant("17:00")).isNull();
  }
}
