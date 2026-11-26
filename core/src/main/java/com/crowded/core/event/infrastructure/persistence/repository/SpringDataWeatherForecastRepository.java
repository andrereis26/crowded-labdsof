package com.crowded.core.event.infrastructure.persistence.repository;

import com.crowded.core.event.infrastructure.persistence.entity.WeatherForecastEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Spring Data access to {@code weather_forecast}. Its deletes run at once, before the rows saved
 * after them are inserted at the flush, so a replaced row never meets the unique constraint.
 */
interface SpringDataWeatherForecastRepository extends JpaRepository<WeatherForecastEntity, UUID> {

  List<WeatherForecastEntity> findByEventId(UUID eventId);

  @Modifying(flushAutomatically = true)
  @Query("delete from WeatherForecastEntity f where f.eventId = :eventId")
  void deleteByEvent(UUID eventId);
}
