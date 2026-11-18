package com.crowded.core.ticket.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * A ticket import that waits for the event code the participant types: the aggregate root of the
 * ticket module while the ticket has no event yet. It keeps the entry code only encrypted, and is
 * kept for {@link #TIME_TO_LIVE_MINUTES} minutes, then forgotten with its data.
 */
public final class TicketImport {

  /** How long a pending import waits for the event code. */
  public static final long TIME_TO_LIVE_MINUTES = 30;

  private final UUID id;
  private final UUID ownerId;
  private final String encryptedEntryCode;
  private final TicketExtraction extraction;

  private TicketImport(
      UUID id, UUID ownerId, String encryptedEntryCode, TicketExtraction extraction) {
    this.id = Objects.requireNonNull(id, "id");
    this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
    if (encryptedEntryCode == null || encryptedEntryCode.isBlank()) {
      throw new IllegalArgumentException("A ticket import keeps its encrypted entry code");
    }
    this.encryptedEntryCode = encryptedEntryCode;
    this.extraction = extraction == null ? TicketExtraction.NOTHING : extraction;
  }

  /** A new import of a ticket by its owner, with what the AI found on it. */
  public static TicketImport start(
      UUID ownerId, String encryptedEntryCode, TicketExtraction extraction) {
    return new TicketImport(UUID.randomUUID(), ownerId, encryptedEntryCode, extraction);
  }

  /** A stored import, as it was saved. */
  public static TicketImport restore(
      UUID id, UUID ownerId, String encryptedEntryCode, TicketExtraction extraction) {
    return new TicketImport(id, ownerId, encryptedEntryCode, extraction);
  }

  /** Whether {@code userId} is the participant who imported the ticket. */
  public boolean isOwnedBy(UUID userId) {
    return ownerId.equals(userId);
  }

  public UUID getId() {
    return id;
  }

  public UUID getOwnerId() {
    return ownerId;
  }

  public String getEncryptedEntryCode() {
    return encryptedEntryCode;
  }

  public TicketExtraction getExtraction() {
    return extraction;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof TicketImport ticketImport && id.equals(ticketImport.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "TicketImport[id=" + id + ", ownerId=" + ownerId + "]";
  }
}
