package com.crowded.core.ticket.infrastructure.persistence.repository;

import com.crowded.core.ticket.infrastructure.persistence.entity.TicketEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Spring Data access to {@code ticket}. */
interface SpringDataTicketRepository extends JpaRepository<TicketEntity, UUID> {

  /** A ticket's owner and event, which are kept on its connection. */
  interface Owner {
    UUID getUserId();

    UUID getEventId();
  }

  @Query(
      value =
          "SELECT c.user_id AS userId, c.event_id AS eventId FROM event_connection c"
              + " WHERE c.id = :ticketId",
      nativeQuery = true)
  Optional<Owner> findOwnerOf(UUID ticketId);

  @Query(value = "SELECT t.id FROM ticket t WHERE t.entry_code_hash = :hash", nativeQuery = true)
  Optional<UUID> findIdByEntryCodeHash(String hash);
}
