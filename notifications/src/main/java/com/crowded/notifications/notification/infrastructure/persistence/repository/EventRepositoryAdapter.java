package com.crowded.notifications.notification.infrastructure.persistence.repository;

import com.crowded.notifications.notification.domain.repository.EventRepository;
import com.crowded.notifications.notification.infrastructure.persistence.entity.EventEntity;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** {@link EventRepository} on Core's {@code event} table in PostgreSQL. */
@Repository
class EventRepositoryAdapter implements EventRepository {

  private final SpringDataEventRepository events;

  EventRepositoryAdapter(SpringDataEventRepository events) {
    this.events = events;
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, String> findNames(Collection<UUID> eventIds) {
    if (eventIds.isEmpty()) {
      return Map.of();
    }
    return events.findByIdIn(eventIds).stream()
        .collect(Collectors.toUnmodifiableMap(EventEntity::getId, EventEntity::getName));
  }
}
