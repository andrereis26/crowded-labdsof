package com.crowded.core.event.domain.model;

import com.crowded.core.shared.domain.exception.RuleViolationException;
import com.crowded.core.shared.domain.model.Image;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The image of an event, at most one, chosen by its organization: an aggregate of its own, so the
 * event does not carry its bytes. Its identity is the event's.
 */
public final class EventImage {

  private final UUID eventId;
  private final Image image;
  private final Instant updatedAt;

  private EventImage(UUID eventId, Image image, Instant updatedAt) {
    this.eventId = Objects.requireNonNull(eventId, "eventId");
    this.image = Objects.requireNonNull(image, "image");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
  }

  /**
   * The image uploaded for the event at {@code now}, without its metadata.
   *
   * @throws RuleViolationException when the file is not an accepted image ({@link Image#uploaded})
   */
  public static EventImage upload(UUID eventId, byte[] file, Instant now) {
    return new EventImage(eventId, Image.uploaded(file), now);
  }

  /** A stored image, as it was saved. */
  public static EventImage restore(UUID eventId, Image image, Instant updatedAt) {
    return new EventImage(eventId, image, updatedAt);
  }

  public UUID getEventId() {
    return eventId;
  }

  public Image getImage() {
    return image;
  }

  /** When it was uploaded: clients use it to know the image changed. */
  public Instant getUpdatedAt() {
    return updatedAt;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof EventImage that && eventId.equals(that.eventId);
  }

  @Override
  public int hashCode() {
    return eventId.hashCode();
  }
}
