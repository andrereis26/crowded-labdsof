package com.crowded.core.event.infrastructure.persistence.entity;

import com.crowded.core.event.domain.model.SourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Row of {@code weather_forecast}: IPMA's forecast for one day of an event. */
@Entity
@Table(name = "weather_forecast")
public class WeatherForecastEntity {

  @Id private UUID id;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "date", nullable = false)
  private LocalDate date;

  @Column(name = "conditions", nullable = false)
  private String conditions;

  @Column(name = "min_temperature", nullable = false)
  private double minTemperature;

  @Column(name = "max_temperature", nullable = false)
  private double maxTemperature;

  @Column(name = "precipitation_probability", nullable = false)
  private double precipitationProbability;

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

  public LocalDate getDate() {
    return date;
  }

  public void setDate(LocalDate date) {
    this.date = date;
  }

  public String getConditions() {
    return conditions;
  }

  public void setConditions(String conditions) {
    this.conditions = conditions;
  }

  public double getMinTemperature() {
    return minTemperature;
  }

  public void setMinTemperature(double minTemperature) {
    this.minTemperature = minTemperature;
  }

  public double getMaxTemperature() {
    return maxTemperature;
  }

  public void setMaxTemperature(double maxTemperature) {
    this.maxTemperature = maxTemperature;
  }

  public double getPrecipitationProbability() {
    return precipitationProbability;
  }

  public void setPrecipitationProbability(double precipitationProbability) {
    this.precipitationProbability = precipitationProbability;
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
