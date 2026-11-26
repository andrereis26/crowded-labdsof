package com.crowded.core.event.infrastructure.persistence.entity;

import com.crowded.core.event.domain.model.SourceType;
import com.crowded.core.event.domain.model.WarningLevel;
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

/** Row of {@code weather_warning}: an IPMA warning during an event. */
@Entity
@Table(name = "weather_warning")
public class WeatherWarningEntity {

  @Id private UUID id;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "awareness_type", nullable = false)
  private String awarenessType;

  @Column(name = "start_time", nullable = false)
  private Instant startTime;

  @Column(name = "end_time", nullable = false)
  private Instant endTime;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "level", columnDefinition = "warning_level", nullable = false)
  private WarningLevel level;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "notified_level", columnDefinition = "warning_level")
  private WarningLevel notifiedLevel;

  @Column(name = "description")
  private String description;

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

  public String getAwarenessType() {
    return awarenessType;
  }

  public void setAwarenessType(String awarenessType) {
    this.awarenessType = awarenessType;
  }

  public Instant getStartTime() {
    return startTime;
  }

  public void setStartTime(Instant startTime) {
    this.startTime = startTime;
  }

  public Instant getEndTime() {
    return endTime;
  }

  public void setEndTime(Instant endTime) {
    this.endTime = endTime;
  }

  public WarningLevel getLevel() {
    return level;
  }

  public void setLevel(WarningLevel level) {
    this.level = level;
  }

  public WarningLevel getNotifiedLevel() {
    return notifiedLevel;
  }

  public void setNotifiedLevel(WarningLevel notifiedLevel) {
    this.notifiedLevel = notifiedLevel;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
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
