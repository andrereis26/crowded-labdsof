package com.crowded.notifications.notification.application.service;

import com.crowded.notifications.notification.application.dto.NotificationItem;
import com.crowded.notifications.notification.application.dto.NotificationList;
import com.crowded.notifications.notification.domain.exception.NotificationNotFoundException;
import com.crowded.notifications.notification.domain.model.Notification;
import com.crowded.notifications.notification.domain.repository.EventRepository;
import com.crowded.notifications.notification.domain.repository.NotificationRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A participant's notification center: only their own notifications (SR-003). */
@Service
public class NotificationCenter {

  private final NotificationRepository notifications;
  private final EventRepository events;
  private final Clock clock;

  NotificationCenter(NotificationRepository notifications, EventRepository events, Clock clock) {
    this.notifications = notifications;
    this.events = events;
    this.clock = clock;
  }

  /** The notifications of {@code userId}, newest first, and how many are unread. */
  @Transactional(readOnly = true)
  public NotificationList list(UUID userId) {
    List<Notification> found = notifications.findByRecipient(userId);
    Set<UUID> eventIds =
        found.stream().map(Notification::getEventId).collect(Collectors.toUnmodifiableSet());
    Map<UUID, String> names = eventIds.isEmpty() ? Map.of() : events.findNames(eventIds);
    List<NotificationItem> items =
        found.stream()
            .map(
                notification ->
                    NotificationItem.of(notification, names.get(notification.getEventId())))
            .toList();
    return new NotificationList(items, notifications.countUnread(userId));
  }

  /**
   * Marks a notification of {@code userId} read. Reading it again changes nothing.
   *
   * @throws NotificationNotFoundException when {@code userId} has no such notification, also when
   *     it is another participant's
   */
  @Transactional
  public void markRead(UUID userId, UUID notificationId) {
    Notification notification =
        notifications
            .findById(notificationId)
            .filter(found -> found.isFor(userId))
            .orElseThrow(() -> new NotificationNotFoundException(notificationId));
    if (!notification.isRead()) {
      notification.markRead(clock.instant());
      notifications.saveStamps(notification);
    }
  }
}
