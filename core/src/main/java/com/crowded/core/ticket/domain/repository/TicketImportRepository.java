package com.crowded.core.ticket.domain.repository;

import com.crowded.core.ticket.domain.model.TicketImport;
import java.util.Optional;
import java.util.UUID;

/**
 * Pending ticket imports, each kept for {@link TicketImport#TIME_TO_LIVE_MINUTES} minutes and then
 * forgotten with its data.
 */
public interface TicketImportRepository {

  /**
   * @throws com.crowded.core.shared.domain.exception.UnavailableException when the store is down
   */
  void save(TicketImport ticketImport);

  /**
   * The pending import, or empty when there is none or it expired.
   *
   * @throws com.crowded.core.shared.domain.exception.UnavailableException when the store is down
   */
  Optional<TicketImport> findById(UUID importId);

  /**
   * Forgets the import with its entry code; nothing happens when there is none.
   *
   * @throws com.crowded.core.shared.domain.exception.UnavailableException when the store is down
   */
  void delete(UUID importId);
}
