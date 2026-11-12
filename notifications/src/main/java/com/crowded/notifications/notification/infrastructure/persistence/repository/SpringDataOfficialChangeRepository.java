package com.crowded.notifications.notification.infrastructure.persistence.repository;

import com.crowded.notifications.notification.infrastructure.persistence.entity.EventChangeEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Spring Data access to Core's {@code event_change}. */
interface SpringDataOfficialChangeRepository extends Repository<EventChangeEntity, UUID> {

  @Query(
      value =
          """
          SELECT id AS "id", event_id AS "eventId", CAST(type AS text) AS "type",
                 (new_value -> 'startDateTime') IS NOT NULL AS "startChanged",
                 (new_value -> 'doorsOpenAt') IS NOT NULL AS "doorsOpenChanged",
                 previous_value ->> 'venueName' AS "previousVenueName",
                 new_value ->> 'venueName' AS "newVenueName",
                 new_value ->> 'address' AS "newAddress",
                 ticket_still_valid AS "ticketStillValid", refund_info AS "refundInfo",
                 message AS "message"
          FROM event_change
          WHERE id = :id""",
      nativeQuery = true)
  Optional<OfficialChangeRow> findChangeById(@Param("id") UUID id);
}
