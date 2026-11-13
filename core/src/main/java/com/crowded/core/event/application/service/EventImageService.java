package com.crowded.core.event.application.service;

import com.crowded.core.audit.application.service.AuditLog;
import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.EventImage;
import com.crowded.core.event.domain.repository.EventImageRepository;
import com.crowded.core.event.domain.repository.EventRepository;
import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.shared.domain.exception.NotFoundException;
import com.crowded.core.shared.domain.exception.RuleViolationException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * An event's image: its organization uploads, replaces or removes it, and whoever may see the event
 * gets it. Images are stored in PostgreSQL without their metadata (ADR 0019).
 */
@Service
public class EventImageService {

  private static final Logger log = LoggerFactory.getLogger(EventImageService.class);

  private final EventRepository events;
  private final EventImageRepository images;
  private final EventAccess access;
  private final AuditLog audit;
  private final Clock clock;
  private final MeterRegistry meters;

  EventImageService(
      EventRepository events,
      EventImageRepository images,
      EventAccess access,
      AuditLog audit,
      Clock clock,
      MeterRegistry meters) {
    this.events = events;
    this.images = images;
    this.access = access;
    this.audit = audit;
    this.clock = clock;
    this.meters = meters;
  }

  /**
   * Stores {@code file} as the event's image, in place of the one it had.
   *
   * @throws NotFoundException when the event does not exist
   * @throws NotAllowedException when the user is not an active member of its organization
   * @throws RuleViolationException when the file is not an accepted image
   */
  @Transactional
  public EventImage replace(UUID userId, UUID eventId, byte[] file) {
    access.checkManager(userId, event(eventId));
    EventImage image = EventImage.upload(eventId, file, clock.instant());
    images.save(image);
    audit.record(
        userId,
        eventId,
        "EVENT_IMAGE_REPLACED",
        "Event",
        eventId,
        Map.<String, Object>of(
            "mediaType", image.getImage().mediaType(), "bytes", image.getImage().size()));
    count("replaced");
    log.info(
        "Stored a {} image of {} bytes for event {} by user {}",
        image.getImage().mediaType(),
        image.getImage().size(),
        eventId,
        userId);
    return image;
  }

  /**
   * Removes the event's image; nothing happens when it had none.
   *
   * @throws NotFoundException when the event does not exist
   * @throws NotAllowedException when the user is not an active member of its organization
   */
  @Transactional
  public void remove(UUID userId, UUID eventId) {
    access.checkManager(userId, event(eventId));
    if (images.deleteByEventId(eventId)) {
      audit.record(userId, eventId, "EVENT_IMAGE_REMOVED", "Event", eventId, Map.of());
      count("removed");
      log.info("Removed the image of event {} by user {}", eventId, userId);
    }
  }

  /**
   * The event's image, for a user who may see the event.
   *
   * @throws NotFoundException when the event does not exist or has no image
   * @throws NotAllowedException when the user may not see the event
   */
  @Transactional(readOnly = true)
  public EventImage find(UUID userId, UUID eventId) {
    access.viewable(userId, eventId);
    return images
        .findByEventId(eventId)
        .orElseThrow(
            () -> new NotFoundException("EVENT_IMAGE_NOT_FOUND", "This event has no image."));
  }

  private Event event(UUID eventId) {
    return events
        .findById(eventId)
        .orElseThrow(() -> new NotFoundException("EVENT_NOT_FOUND", "There is no such event."));
  }

  private void count(String action) {
    Counter.builder("crowded.event.images")
        .description("Event images stored or removed")
        .tag("action", action)
        .register(meters)
        .increment();
  }
}
