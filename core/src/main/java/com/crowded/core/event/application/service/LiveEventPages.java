package com.crowded.core.event.application.service;

import com.crowded.core.event.application.dto.EventPage;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The live streams of event pages open on this instance, and the delivery of a changed page to them
 * (ADR 0007). Who may open a stream is checked when it opens.
 */
@Service
public class LiveEventPages {

  private static final Logger log = LoggerFactory.getLogger(LiveEventPages.class);

  private final EventPages pages;
  private final Map<UUID, List<Consumer<EventPage>>> listeners = new ConcurrentHashMap<>();

  LiveEventPages(EventPages pages) {
    this.pages = pages;
  }

  /**
   * Sends the event's page to {@code listener} each time it changes, until the returned action
   * runs. A listener that throws is removed.
   *
   * @return closes the subscription; running it again does nothing
   */
  public Runnable subscribe(UUID eventId, Consumer<EventPage> listener) {
    listeners.compute(
        eventId,
        (id, streams) -> {
          List<Consumer<EventPage>> open = streams == null ? new CopyOnWriteArrayList<>() : streams;
          open.add(listener);
          return open;
        });
    return () -> unsubscribe(eventId, listener);
  }

  /** Sends the event's current page to its streams on this instance, if it has any. */
  public void deliver(UUID eventId) {
    List<Consumer<EventPage>> streams = listeners.get(eventId);
    if (streams == null || streams.isEmpty()) {
      return;
    }
    EventPage page = pages.current(eventId);
    int sent = 0;
    for (Consumer<EventPage> stream : streams) {
      try {
        stream.accept(page);
        sent++;
      } catch (RuntimeException e) {
        log.debug("Stream of event {} closed: {}", eventId, e.toString());
        unsubscribe(eventId, stream);
      }
    }
    log.info("Page of event {} sent on {} stream(s)", eventId, sent);
  }

  private void unsubscribe(UUID eventId, Consumer<EventPage> listener) {
    listeners.computeIfPresent(
        eventId,
        (id, streams) -> {
          streams.remove(listener);
          return streams.isEmpty() ? null : streams;
        });
  }
}
