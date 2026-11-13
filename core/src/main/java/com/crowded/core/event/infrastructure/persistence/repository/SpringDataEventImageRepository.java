package com.crowded.core.event.infrastructure.persistence.repository;

import com.crowded.core.event.infrastructure.persistence.entity.EventImageEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Spring Data access to {@code event_image}. */
interface SpringDataEventImageRepository extends JpaRepository<EventImageEntity, UUID> {

  /** When an image was uploaded, read without its bytes. */
  interface UploadTime {
    UUID getEventId();

    Instant getUpdatedAt();
  }

  List<UploadTime> findByEventIdIn(Collection<UUID> eventIds);

  @Modifying
  @Query("delete from EventImageEntity i where i.eventId = :eventId")
  int deleteImage(UUID eventId);
}
