package com.crowded.jobs.weather.infrastructure.persistence.entity;

import com.crowded.jobs.weather.domain.model.SnapshotKind;
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

/** Row of {@code weather_snapshot}. */
@Entity
@Table(name = "weather_snapshot")
public class WeatherSnapshotEntity {

  @Id private UUID id;

  @Column(nullable = false)
  private String district;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(columnDefinition = "weather_snapshot_kind", nullable = false)
  private SnapshotKind kind;

  @Column(columnDefinition = "jsonb", nullable = false)
  @ColumnTransformer(write = "?::jsonb")
  private String content;

  @Column(name = "fetched_at", nullable = false)
  private Instant fetchedAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getDistrict() {
    return district;
  }

  public void setDistrict(String district) {
    this.district = district;
  }

  public SnapshotKind getKind() {
    return kind;
  }

  public void setKind(SnapshotKind kind) {
    this.kind = kind;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public Instant getFetchedAt() {
    return fetchedAt;
  }

  public void setFetchedAt(Instant fetchedAt) {
    this.fetchedAt = fetchedAt;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public void setPublishedAt(Instant publishedAt) {
    this.publishedAt = publishedAt;
  }
}
