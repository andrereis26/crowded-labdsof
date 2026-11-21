package com.crowded.core.event.infrastructure.persistence.entity;

import com.crowded.core.event.domain.model.SourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Row of {@code queue_time}: the current waiting time at one of an event's queue points. */
@Entity
@Table(name = "queue_time")
public class QueueTimeEntity {

  @Id private UUID id;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "queue_point_id", nullable = false)
  private UUID queuePointId;

  @Column(name = "wait_minutes", nullable = false)
  private int waitMinutes;

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

  public UUID getQueuePointId() {
    return queuePointId;
  }

  public void setQueuePointId(UUID queuePointId) {
    this.queuePointId = queuePointId;
  }

  public int getWaitMinutes() {
    return waitMinutes;
  }

  public void setWaitMinutes(int waitMinutes) {
    this.waitMinutes = waitMinutes;
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
