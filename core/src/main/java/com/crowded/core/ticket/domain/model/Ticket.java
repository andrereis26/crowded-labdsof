package com.crowded.core.ticket.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * A participant's ticket to a registered event, stored once the event code matched. It shares its
 * id with the participant's connection to the event, and keeps the entry code only encrypted, with
 * a keyed hash that links the ticket to one account.
 */
public final class Ticket {

  private final UUID id;
  private final UUID ownerId;
  private final UUID eventId;
  private final TicketDetails details;
  private final String encryptedEntryCode;
  private final EntryCodeHash entryCodeHash;
  private final ImportMethod importMethod;
  private final MatchMethod matchMethod;

  private Ticket(
      UUID id,
      UUID ownerId,
      UUID eventId,
      TicketDetails details,
      String encryptedEntryCode,
      EntryCodeHash entryCodeHash,
      ImportMethod importMethod,
      MatchMethod matchMethod) {
    this.id = Objects.requireNonNull(id, "id");
    this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
    this.eventId = Objects.requireNonNull(eventId, "eventId");
    this.details = details == null ? TicketDetails.NONE : details;
    if (encryptedEntryCode == null || encryptedEntryCode.isBlank()) {
      throw new IllegalArgumentException("A ticket keeps its encrypted entry code");
    }
    this.encryptedEntryCode = encryptedEntryCode;
    this.entryCodeHash = Objects.requireNonNull(entryCodeHash, "entryCodeHash");
    this.importMethod = Objects.requireNonNull(importMethod, "importMethod");
    this.matchMethod = Objects.requireNonNull(matchMethod, "matchMethod");
  }

  /**
   * A ticket of {@code ownerId} to {@code eventId}, connected with {@code connectionId}. Its
   * details are what was read from the ticket, never used to decide anything.
   */
  public static Ticket connect(
      UUID connectionId,
      UUID ownerId,
      UUID eventId,
      TicketDetails details,
      String encryptedEntryCode,
      EntryCodeHash entryCodeHash,
      ImportMethod importMethod,
      MatchMethod matchMethod) {
    return new Ticket(
        connectionId,
        ownerId,
        eventId,
        details,
        encryptedEntryCode,
        entryCodeHash,
        importMethod,
        matchMethod);
  }

  /** A stored ticket, as it was saved. */
  public static Ticket restore(
      UUID id,
      UUID ownerId,
      UUID eventId,
      TicketDetails details,
      String encryptedEntryCode,
      EntryCodeHash entryCodeHash,
      ImportMethod importMethod,
      MatchMethod matchMethod) {
    return new Ticket(
        id,
        ownerId,
        eventId,
        details,
        encryptedEntryCode,
        entryCodeHash,
        importMethod,
        matchMethod);
  }

  public boolean isOwnedBy(UUID userId) {
    return ownerId.equals(userId);
  }

  public UUID getId() {
    return id;
  }

  public UUID getEventId() {
    return eventId;
  }

  public TicketDetails getDetails() {
    return details;
  }

  public String getEncryptedEntryCode() {
    return encryptedEntryCode;
  }

  public EntryCodeHash getEntryCodeHash() {
    return entryCodeHash;
  }

  public ImportMethod getImportMethod() {
    return importMethod;
  }

  public MatchMethod getMatchMethod() {
    return matchMethod;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Ticket ticket && id.equals(ticket.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "Ticket[id=" + id + ", eventId=" + eventId + "]";
  }
}
