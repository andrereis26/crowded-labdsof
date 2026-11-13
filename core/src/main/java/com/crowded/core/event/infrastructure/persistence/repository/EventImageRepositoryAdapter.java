package com.crowded.core.event.infrastructure.persistence.repository;

import com.crowded.core.event.domain.model.EventImage;
import com.crowded.core.event.domain.repository.EventImageRepository;
import com.crowded.core.event.infrastructure.persistence.mapper.EventImagePersistenceMapper;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/** {@link EventImageRepository} on PostgreSQL (ADR 0019). */
@Repository
class EventImageRepositoryAdapter implements EventImageRepository {

  private final SpringDataEventImageRepository images;
  private final EventImagePersistenceMapper mapper;

  EventImageRepositoryAdapter(
      SpringDataEventImageRepository images, EventImagePersistenceMapper mapper) {
    this.images = images;
    this.mapper = mapper;
  }

  @Override
  public Optional<EventImage> findByEventId(UUID eventId) {
    return images.findById(eventId).map(mapper::toDomain);
  }

  @Override
  public Map<UUID, Instant> findUpdatedAt(Collection<UUID> eventIds) {
    if (eventIds.isEmpty()) {
      return Map.of();
    }
    return images.findByEventIdIn(eventIds).stream()
        .collect(
            Collectors.toUnmodifiableMap(
                SpringDataEventImageRepository.UploadTime::getEventId,
                SpringDataEventImageRepository.UploadTime::getUpdatedAt));
  }

  @Override
  public void save(EventImage image) {
    images.save(mapper.toEntity(image));
  }

  @Override
  public boolean deleteByEventId(UUID eventId) {
    return images.deleteImage(eventId) > 0;
  }
}
