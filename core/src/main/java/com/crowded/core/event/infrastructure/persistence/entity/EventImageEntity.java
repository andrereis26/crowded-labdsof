package com.crowded.core.event.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Row of {@code event_image}: an event's image and its media type. */
@Entity
@Table(name = "event_image")
public class EventImageEntity {

  @Id
  @Column(name = "event_id")
  private UUID eventId;

  @Column(name = "media_type", nullable = false)
  private String mediaType;

  @Column(nullable = false)
  private byte[] content;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public UUID getEventId() {
    return eventId;
  }

  public void setEventId(UUID eventId) {
    this.eventId = eventId;
  }

  public String getMediaType() {
    return mediaType;
  }

  public void setMediaType(String mediaType) {
    this.mediaType = mediaType;
  }

  public byte[] getContent() {
    return content == null ? null : content.clone();
  }

  public void setContent(byte[] content) {
    this.content = content == null ? null : content.clone();
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
