package com.crowded.core.ticket.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

/** Row of {@code ticket_extraction}: what the AI read from a stored ticket. */
@Entity
@Table(name = "ticket_extraction")
public class TicketExtractionEntity {

  @Id private UUID id;

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

  public UUID getId() {
    return id;
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
