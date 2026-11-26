package com.crowded.notifications.notification.infrastructure.persistence.repository;

import com.crowded.notifications.notification.infrastructure.persistence.entity.EventEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Spring Data access to Core's {@code weather_warning}, with its event's district. */
interface SpringDataWeatherWarningRepository extends Repository<EventEntity, UUID> {

  @Query(
      value =
          """
          SELECT w.id AS "id", w.event_id AS "eventId", w.awareness_type AS "awarenessType",
                 CAST(w.level AS text) AS "level", w.start_time AS "startTime",
                 w.end_time AS "endTime", w.description AS "description", e.district AS "district"
          FROM weather_warning w JOIN event e ON e.id = w.event_id
          WHERE w.id = :id""",
      nativeQuery = true)
  Optional<WeatherWarningRow> findWarningById(@Param("id") UUID id);
}
