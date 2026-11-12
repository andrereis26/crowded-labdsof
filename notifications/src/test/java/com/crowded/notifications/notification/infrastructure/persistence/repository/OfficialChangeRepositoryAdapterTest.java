package com.crowded.notifications.notification.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crowded.notifications.notification.domain.model.ChangeType;
import com.crowded.notifications.notification.domain.model.OfficialChange;
import com.crowded.notifications.notification.infrastructure.persistence.mapper.OfficialChangePersistenceMapperImpl;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OfficialChangeRepositoryAdapterTest {

  private final SpringDataOfficialChangeRepository rows =
      mock(SpringDataOfficialChangeRepository.class);
  private final OfficialChangeRepositoryAdapter changes =
      new OfficialChangeRepositoryAdapter(rows, new OfficialChangePersistenceMapperImpl());

  @Test
  void readsTheChangedValues() {
    UUID id = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    OfficialChangeRow row = mock(OfficialChangeRow.class);
    when(row.getId()).thenReturn(id);
    when(row.getEventId()).thenReturn(eventId);
    when(row.getType()).thenReturn("POSTPONEMENT");
    when(row.getPreviousStart()).thenReturn("2025-10-25T20:30:00Z");
    when(row.getNewStart()).thenReturn("2025-11-08T21:30:00Z");
    when(row.getPreviousDoorsOpenAt()).thenReturn("19:30");
    when(row.getNewDoorsOpenAt()).thenReturn(null);
    when(row.getPreviousVenueName()).thenReturn("Teatro Municipal");
    when(row.getNewVenueName()).thenReturn("Coliseu");
    when(row.getNewAddress()).thenReturn("Rua de Passos Manuel");
    when(row.getTicketStillValid()).thenReturn(true);
    when(row.getRefundInfo()).thenReturn("Até 31 OUT");
    when(row.getMessage()).thenReturn("Até breve.");
    when(rows.findChangeById(id)).thenReturn(Optional.of(row));

    assertThat(changes.findById(id))
        .contains(
            new OfficialChange(
                id,
                eventId,
                ChangeType.POSTPONEMENT,
                Instant.parse("2025-10-25T20:30:00Z"),
                Instant.parse("2025-11-08T21:30:00Z"),
                null,
                null,
                "Teatro Municipal",
                "Coliseu",
                "Rua de Passos Manuel",
                true,
                "Até 31 OUT",
                "Até breve."));
  }

  @Test
  void findsNothingForAnUnknownChange() {
    UUID id = UUID.randomUUID();
    when(rows.findChangeById(id)).thenReturn(Optional.empty());

    assertThat(changes.findById(id)).isEmpty();
  }
}
