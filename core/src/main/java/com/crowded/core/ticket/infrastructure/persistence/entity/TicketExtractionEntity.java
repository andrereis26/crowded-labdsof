package com.crowded.core.ticket.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;
import org.springframework.data.domain.Persistable;

/**
 * Row of {@code ticket_extraction}: what the AI read from a stored ticket. It is only ever added,
 * so a new one is inserted without reading it first.
 */
@Entity
@Table(name = "ticket_extraction")
public class TicketExtractionEntity implements Persistable<UUID> {

  @Id private UUID id;

  @Transient private boolean stored;

  @Column(name = "ticket_id", nullable = false)
  private UUID ticketId;

  @Column(name = "extracted_event_code")
  private String extractedEventCode;

  @Column(name = "extracted_fields", columnDefinition = "jsonb", nullable = false)
  @ColumnTransformer(write = "?::jsonb")
  private String extractedFields;

  @Column(name = "ai_generated", nullable = false)
  private boolean aiGenerated;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return !stored;
  }

  @PostLoad
  @PostPersist
  void markStored() {
    stored = true;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getTicketId() {
    return ticketId;
  }

  public void setTicketId(UUID ticketId) {
    this.ticketId = ticketId;
  }

  public String getExtractedEventCode() {
    return extractedEventCode;
  }

  public void setExtractedEventCode(String extractedEventCode) {
    this.extractedEventCode = extractedEventCode;
  }

  public String getExtractedFields() {
    return extractedFields;
  }

  public void setExtractedFields(String extractedFields) {
    this.extractedFields = extractedFields;
  }

  public boolean isAiGenerated() {
    return aiGenerated;
  }

  public void setAiGenerated(boolean aiGenerated) {
    this.aiGenerated = aiGenerated;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
