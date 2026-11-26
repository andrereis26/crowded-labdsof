package com.crowded.jobs.weather.infrastructure.persistence.repository;

import com.crowded.jobs.weather.infrastructure.persistence.entity.EventEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Spring Data access to Core's {@code event}. */
interface SpringDataEventRepository extends Repository<EventEntity, UUID> {

  @Query(
      value =
          """
          SELECT DISTINCT district FROM event
          WHERE end_date_time > :now AND start_date_time < :until
            AND status NOT IN ('CANCELLED', 'ENDED')
          ORDER BY district""",
      nativeQuery = true)
  List<String> findDistrictsBetween(@Param("now") Instant now, @Param("until") Instant until);
}
