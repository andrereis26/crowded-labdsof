package com.crowded.jobs.weather.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * What Jobs fetched from IPMA for one district at one time: its warnings or its forecast. A new
 * snapshot is kept only when its content differs from the district's latest one, and stays
 * unpublished until RabbitMQ confirms its {@link WeatherUpdated}.
 */
public final class WeatherSnapshot {

  private final UUID id;
  private final String district;
  private final WeatherContent content;
  private final Instant fetchedAt;
  private Instant publishedAt;

  private WeatherSnapshot(
      UUID id, String district, WeatherContent content, Instant fetchedAt, Instant publishedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.district = Objects.requireNonNull(district, "district");
    this.content = Objects.requireNonNull(content, "content");
    this.fetchedAt = Objects.requireNonNull(fetchedAt, "fetchedAt");
    this.publishedAt = publishedAt;
  }

  /** What was fetched for {@code district} at {@code now}, not yet published. */
  public static WeatherSnapshot take(String district, WeatherContent content, Instant now) {
    return new WeatherSnapshot(UUID.randomUUID(), district, content, now, null);
  }

  /** A stored snapshot, as it was saved. */
  public static WeatherSnapshot restore(
      UUID id, String district, WeatherContent content, Instant fetchedAt, Instant publishedAt) {
    return new WeatherSnapshot(id, district, content, fetchedAt, publishedAt);
  }

  /** Whether this snapshot says something {@code latest}, the district's last one, did not. */
  public boolean isNewComparedTo(Optional<WeatherSnapshot> latest) {
    return latest.map(previous -> !content.equals(previous.content)).orElse(true);
  }

  public SnapshotKind kind() {
    return content.kind();
  }

  /** The message that tells Core about it. */
  public WeatherUpdated announcement() {
    return new WeatherUpdated(district, id);
  }

  /** RabbitMQ confirmed its message at {@code at}. */
  public void markPublished(Instant at) {
    publishedAt = Objects.requireNonNull(at, "at");
  }

  public UUID getId() {
    return id;
  }

  public String getDistrict() {
    return district;
  }

  public WeatherContent getContent() {
    return content;
  }

  public Instant getFetchedAt() {
    return fetchedAt;
  }

  /** When RabbitMQ confirmed its message; null until then. */
  public Instant getPublishedAt() {
    return publishedAt;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof WeatherSnapshot snapshot && id.equals(snapshot.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "WeatherSnapshot[" + id + ", " + district + ", " + kind() + "]";
  }
}
