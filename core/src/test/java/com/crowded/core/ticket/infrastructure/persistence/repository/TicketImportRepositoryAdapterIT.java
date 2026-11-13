package com.crowded.core.ticket.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.crowded.core.IntegrationTest;
import com.crowded.core.ticket.domain.model.TicketDetails;
import com.crowded.core.ticket.domain.model.TicketExtraction;
import com.crowded.core.ticket.domain.model.TicketImport;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Pending imports are kept in Redis for 30 minutes, as they were saved. */
@ResourceLock(IntegrationTest.SHARED_STATE)
class TicketImportRepositoryAdapterIT extends IntegrationTest {

  @Autowired private TicketImportRepositoryAdapter imports;
  @Autowired private StringRedisTemplate redis;

  @Test
  void keepsAPendingImportForThirtyMinutesAsItWasSaved() {
    TicketExtraction extraction =
        new TicketExtraction("MARG-2026", new TicketDetails("A", "2", "14"));
    TicketImport pending = TicketImport.start(UUID.randomUUID(), "ZW5jcnlwdGVk", extraction);

    imports.save(pending);

    assertThat(imports.findById(pending.getId()))
        .hasValueSatisfying(
            found -> {
              assertThat(found).isEqualTo(pending);
              assertThat(found.getOwnerId()).isEqualTo(pending.getOwnerId());
              assertThat(found.getEncryptedEntryCode()).isEqualTo("ZW5jcnlwdGVk");
              assertThat(found.getExtraction()).isEqualTo(extraction);
            });
    assertThat(
            redis.getExpire(
                TicketImportRepositoryAdapter.PREFIX + pending.getId(), TimeUnit.SECONDS))
        .isBetween(1790L, 1800L);
  }

  @Test
  void findsNothingForAnUnknownImport() {
    assertThat(imports.findById(UUID.randomUUID())).isEmpty();
  }
}
