package com.crowded.notifications.notification.application.service;

import com.crowded.notifications.notification.application.dto.LiveNotification;
import com.crowded.notifications.notification.application.dto.NotificationItem;
import com.crowded.notifications.notification.domain.model.Notification;
import com.crowded.notifications.notification.domain.repository.EventRepository;
import com.crowded.notifications.notification.domain.repository.NotificationRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The live streams open on this instance, and the delivery of new notifications to them. A
 * notification reaches only its recipient's streams (SR-003). What a stream misses while closed is
 * waiting in the {@link NotificationCenter}.
 */
@Service
public class LiveNotifications {

  private static final Logger log = LoggerFactory.getLogger(LiveNotifications.class);

  private final NotificationRepository notifications;
  private final EventRepository events;
  private final Clock clock;
  private final Map<UUID, List<Consumer<LiveNotification>>> listeners = new ConcurrentHashMap<>();

  LiveNotifications(NotificationRepository notifications, EventRepository events, Clock clock) {
    this.notifications = notifications;
    this.events = events;
    this.clock = clock;
  }

  /**
   * Sends each new notification of {@code userId} to {@code listener} until the returned action
   * runs. A listener that throws is removed.
   *
   * @return closes the subscription; running it again does nothing
   */
  public Runnable subscribe(UUID userId, Consumer<LiveNotification> listener) {
    listeners.compute(
        userId,
        (id, streams) -> {
          List<Consumer<LiveNotification>> open =
              streams == null ? new CopyOnWriteArrayList<>() : streams;
          open.add(listener);
          return open;
        });
    return () -> unsubscribe(userId, listener);
  }

  /**
   * Sends the notification {@code notificationId} to its recipient's streams on this instance, and
   * stamps it delivered when one of them took it. Does nothing when it no longer exists or its
   * recipient has no stream here.
   */
  public void deliver(UUID notificationId) {
    Optional<Notification> found = notifications.findById(notificationId);
    if (found.isEmpty()) {
      return;
    }
    Notification notification = found.get();
    List<Consumer<LiveNotification>> streams = listeners.get(notification.getUserId());
    if (streams == null || streams.isEmpty()) {
      return;
    }
    String eventName =
        events.findNames(Set.of(notification.getEventId())).get(notification.getEventId());
    LiveNotification live =
        new LiveNotification(
            NotificationItem.of(notification, eventName),
            notifications.countUnread(notification.getUserId()));
    int sent = 0;
    for (Consumer<LiveNotification> stream : streams) {
      try {
        stream.accept(live);
        sent++;
      } catch (RuntimeException e) {
        log.debug("Stream of {} closed: {}", notification.getUserId(), e.toString());
        unsubscribe(notification.getUserId(), stream);
      }
    }
    if (sent > 0) {
      notification.markDelivered(clock.instant());
      notifications.saveStamps(notification);
    }
    log.info("Notification {} sent on {} stream(s)", notificationId, sent);
  }

  private void unsubscribe(UUID userId, Consumer<LiveNotification> listener) {
    listeners.computeIfPresent(
        userId,
        (id, streams) -> {
          streams.remove(listener);
          return streams.isEmpty() ? null : streams;
        });
  }
}
