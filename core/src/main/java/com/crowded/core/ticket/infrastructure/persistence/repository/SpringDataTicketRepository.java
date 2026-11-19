package com.crowded.core.ticket.infrastructure.persistence.repository;

import com.crowded.core.ticket.infrastructure.persistence.entity.TicketEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Spring Data access to {@code ticket}. */
interface SpringDataTicketRepository extends JpaRepository<TicketEntity, UUID> {

  Optional<TicketEntity> findByEntryCodeHash(String entryCodeHash);

  /** The tickets whose connection belongs to {@code ownerId}. */
  @Query(
      value =
          "SELECT t.* FROM ticket t JOIN event_connection c ON c.id = t.id WHERE c.user_id = :ownerId",
      nativeQuery = true)
  List<TicketEntity> findByOwner(UUID ownerId);

  /** The participant a ticket belongs to, which its connection keeps. */
  @Query(
      value = "SELECT c.user_id FROM event_connection c WHERE c.id = :ticketId",
      nativeQuery = true)
  Optional<UUID> findOwnerOf(UUID ticketId);
}
