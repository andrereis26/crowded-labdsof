package com.crowded.core.event.domain.repository;

import com.crowded.core.event.domain.model.EventImage;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Events' images. */
public interface EventImageRepository {

  /** The event's image, or empty when it has none. */
  Optional<EventImage> findByEventId(UUID eventId);

  /**
   * When each event's image was uploaded, read without the bytes; events without one are absent.
   */
  Map<UUID, Instant> findUpdatedAt(Collection<UUID> eventIds);

  /** Stores the image, in place of the event's previous one. */
  void save(EventImage image);

  /**
   * Deletes the event's image.
   *
   * @return false when it had none
   */
  boolean deleteByEventId(UUID eventId);
}
