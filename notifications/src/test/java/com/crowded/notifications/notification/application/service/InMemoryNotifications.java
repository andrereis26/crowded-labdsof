package com.crowded.notifications.notification.application.service;

import com.crowded.notifications.notification.domain.model.Notification;
import com.crowded.notifications.notification.domain.repository.NotificationRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link NotificationRepository} in memory, keeping the port's contract: the first notification per
 * deduplication key, newest first, and stamps that are never cleared.
 */
final class InMemoryNotifications implements NotificationRepository {

  final List<Notification> stored = new ArrayList<>();
  int stampsSaved;

  @Override
  public boolean addIfAbsent(Notification notification) {
    if (stored.stream()
        .anyMatch(n -> n.getDeduplicationKey().equals(notification.getDeduplicationKey()))) {
      return false;
    }
    return stored.add(notification);
  }

  @Override
  public Optional<Notification> findById(UUID id) {
    return stored.stream().filter(n -> n.getId().equals(id)).findFirst();
  }

  @Override
  public List<Notification> findByRecipient(UUID userId) {
    return stored.stream()
        .filter(n -> n.isFor(userId))
        .sorted(Comparator.comparing(Notification::getCreatedAt).reversed())
        .toList();
  }

  @Override
  public long countUnread(UUID userId) {
    return stored.stream().filter(n -> n.isFor(userId) && !n.isRead()).count();
  }

  @Override
  public void saveStamps(Notification notification) {
    stampsSaved++;
  }
}
