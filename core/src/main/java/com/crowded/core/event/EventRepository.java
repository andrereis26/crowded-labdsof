package com.crowded.core.event;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** Events. */
interface EventRepository extends JpaRepository<Event, UUID> {

  /**
   * The event, locked until the transaction ends, so two changes to one event are decided one after
   * the other and a resent change finds the first one applied.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Event> findForUpdateById(UUID id);
}
