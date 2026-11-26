package com.crowded.core.event.infrastructure.persistence.repository;

import com.crowded.core.event.infrastructure.persistence.entity.WeatherWarningEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Spring Data access to {@code weather_warning}. Its deletes run at once, before the rows saved
 * after them are inserted at the flush, so a replaced row never meets the unique constraint.
 */
interface SpringDataWeatherWarningRepository extends JpaRepository<WeatherWarningEntity, UUID> {

  List<WeatherWarningEntity> findByEventId(UUID eventId);

  /** Deletes the event's warnings other than {@code kept}, which must not be empty. */
  @Modifying(flushAutomatically = true)
  @Query("delete from WeatherWarningEntity w where w.eventId = :eventId and w.id not in :kept")
  void deleteByEventExcept(UUID eventId, Collection<UUID> kept);

  @Modifying(flushAutomatically = true)
  @Query("delete from WeatherWarningEntity w where w.eventId = :eventId")
  void deleteByEvent(UUID eventId);
}
