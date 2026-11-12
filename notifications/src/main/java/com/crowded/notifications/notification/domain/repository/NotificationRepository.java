package com.crowded.notifications.notification.domain.repository;

import com.crowded.notifications.notification.domain.model.Notification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The notifications this service writes. */
public interface NotificationRepository {

  /**
   * Stores {@code notification} unless one with its deduplication key exists.
   *
   * @return whether it was stored
   */
  boolean addIfAbsent(Notification notification);

  /** The notification with this id, whoever its recipient is; empty when there is none. */
  Optional<Notification> findById(UUID id);

  /** The notifications of {@code userId}, newest first; empty when there are none. */
  List<Notification> findByRecipient(UUID userId);

  /** How many notifications of {@code userId} are unread. */
  long countUnread(UUID userId);

  /**
   * Stores when {@code notification} was delivered and read. A time already stored is kept, so a
   * concurrent update never clears or moves it.
   */
  void saveStamps(Notification notification);
}
