package com.crowded.core.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** An official change to an event, with the organization as its source (FR-016). */
@Entity
@Table(name = "event_change")
public class EventChange {

  @Id private UUID id;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(columnDefinition = "change_type", nullable = false)
  private ChangeType type;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(columnDefinition = "severity", nullable = false)
  private Severity severity;

  @Column(name = "previous_value", columnDefinition = "jsonb", nullable = false)
  @ColumnTransformer(write = "?::jsonb")
  private String previousValue;

  @Column(name = "new_value", columnDefinition = "jsonb", nullable = false)
  @ColumnTransformer(write = "?::jsonb")
  private String newValue;

  @Column(name = "ticket_still_valid")
  private Boolean ticketStillValid;

  @Column(name = "refund_info")
  private String refundInfo;

  private String message;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "provenance_source_type", columnDefinition = "source_type", nullable = false)
  private SourceType provenanceSourceType;

  @Column(name = "provenance_source_name", nullable = false)
  private String provenanceSourceName;

  @Column(name = "provenance_last_updated_at", nullable = false)
  private Instant provenanceLastUpdatedAt;

  @Column(name = "provenance_confirmed", nullable = false)
  private boolean provenanceConfirmed;

  protected EventChange() {}

  /** A change published by the organization {@code organizationName}, confirmed by it. */
  EventChange(
      UUID eventId,
      ChangePlan plan,
      String previousValue,
      String newValue,
      String organizationName,
      Instant publishedAt) {
    this.id = UUID.randomUUID();
    this.eventId = eventId;
    this.type = plan.type();
    this.severity = plan.severity();
    this.previousValue = previousValue;
    this.newValue = newValue;
    this.ticketStillValid = plan.ticketStillValid();
    this.refundInfo = plan.refundInfo();
    this.message = plan.message();
    this.provenanceSourceType = SourceType.ORGANIZATION;
    this.provenanceSourceName = organizationName;
    this.provenanceLastUpdatedAt = publishedAt;
    this.provenanceConfirmed = true;
  }

  public UUID getId() {
    return id;
  }

  public ChangeType getType() {
    return type;
  }

  public Severity getSeverity() {
    return severity;
  }

  public String getPreviousValue() {
    return previousValue;
  }

  public String getNewValue() {
    return newValue;
  }

  public Boolean getTicketStillValid() {
    return ticketStillValid;
  }

  public String getRefundInfo() {
    return refundInfo;
  }

  public String getMessage() {
    return message;
  }

  public String getProvenanceSourceName() {
    return provenanceSourceName;
  }
}
