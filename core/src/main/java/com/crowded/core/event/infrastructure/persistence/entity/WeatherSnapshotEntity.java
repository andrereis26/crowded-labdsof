package com.crowded.core.event.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Row of Jobs' {@code weather_snapshot}, read only (ADR 0002). */
@Entity
@Immutable
@Table(name = "weather_snapshot")
public class WeatherSnapshotEntity {

  @Id private UUID id;

  @Column(nullable = false)
  private String district;

  @Column(columnDefinition = "weather_snapshot_kind", nullable = false)
  private String kind;

  @Column(columnDefinition = "jsonb", nullable = false)
  private String content;

  @Column(name = "fetched_at", nullable = false)
  private Instant fetchedAt;

  public UUID getId() {
    return id;
  }

  public String getDistrict() {
    return district;
  }

  public String getKind() {
    return kind;
  }

  public String getContent() {
    return content;
  }

  public Instant getFetchedAt() {
    return fetchedAt;
  }
}
