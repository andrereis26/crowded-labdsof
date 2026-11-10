package com.crowded.core.event.infrastructure.persistence.entity;

import com.crowded.core.event.domain.model.ChangeType;
import com.crowded.core.event.domain.model.Severity;
import com.crowded.core.event.domain.model.SourceType;
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

/** Row of {@code event_change}. */
@Entity
@Table(name = "event_change")
public class EventChangeEntity {

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

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getEventId() {
    return eventId;
  }

  public void setEventId(UUID eventId) {
    this.eventId = eventId;
  }

  public ChangeType getType() {
    return type;
  }

  public void setType(ChangeType type) {
    this.type = type;
  }

  public Severity getSeverity() {
    return severity;
  }

  public void setSeverity(Severity severity) {
    this.severity = severity;
  }

  public String getPreviousValue() {
    return previousValue;
  }

  public void setPreviousValue(String previousValue) {
    this.previousValue = previousValue;
  }

  public String getNewValue() {
    return newValue;
  }

  public void setNewValue(String newValue) {
    this.newValue = newValue;
  }

  public Boolean getTicketStillValid() {
    return ticketStillValid;
  }

  public void setTicketStillValid(Boolean ticketStillValid) {
    this.ticketStillValid = ticketStillValid;
  }

  public String getRefundInfo() {
    return refundInfo;
  }

  public void setRefundInfo(String refundInfo) {
    this.refundInfo = refundInfo;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public SourceType getProvenanceSourceType() {
    return provenanceSourceType;
  }

  public void setProvenanceSourceType(SourceType provenanceSourceType) {
    this.provenanceSourceType = provenanceSourceType;
  }

  public String getProvenanceSourceName() {
    return provenanceSourceName;
  }

  public void setProvenanceSourceName(String provenanceSourceName) {
    this.provenanceSourceName = provenanceSourceName;
  }

  public Instant getProvenanceLastUpdatedAt() {
    return provenanceLastUpdatedAt;
  }

  public void setProvenanceLastUpdatedAt(Instant provenanceLastUpdatedAt) {
    this.provenanceLastUpdatedAt = provenanceLastUpdatedAt;
  }

  public boolean isProvenanceConfirmed() {
    return provenanceConfirmed;
  }

  public void setProvenanceConfirmed(boolean provenanceConfirmed) {
    this.provenanceConfirmed = provenanceConfirmed;
  }
}
