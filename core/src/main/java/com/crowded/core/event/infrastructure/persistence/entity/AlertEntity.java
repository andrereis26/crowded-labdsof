package com.crowded.core.event.infrastructure.persistence.entity;

import com.crowded.core.event.domain.model.AlertType;
import com.crowded.core.event.domain.model.Severity;
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

/** Row of {@code alert}. Core writes only sent alerts, without an incident. */
@Entity
@Table(name = "alert")
public class AlertEntity {

  @Id private UUID id;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(columnDefinition = "alert_type", nullable = false)
  private AlertType type;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(columnDefinition = "severity", nullable = false)
  private Severity severity;

  @Column(nullable = false)
  private String message;

  @Column(columnDefinition = "alert_status", nullable = false)
  @ColumnTransformer(write = "?::alert_status")
  private String status;

  @Column(name = "sent_at")
  private Instant sentAt;

  @Column(name = "recipients_count")
  private Integer recipientsCount;

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

  public UUID getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(UUID organizationId) {
    this.organizationId = organizationId;
  }

  public AlertType getType() {
    return type;
  }

  public void setType(AlertType type) {
    this.type = type;
  }

  public Severity getSeverity() {
    return severity;
  }

  public void setSeverity(Severity severity) {
    this.severity = severity;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Instant getSentAt() {
    return sentAt;
  }

  public void setSentAt(Instant sentAt) {
    this.sentAt = sentAt;
  }

  public Integer getRecipientsCount() {
    return recipientsCount;
  }

  public void setRecipientsCount(Integer recipientsCount) {
    this.recipientsCount = recipientsCount;
  }
}
