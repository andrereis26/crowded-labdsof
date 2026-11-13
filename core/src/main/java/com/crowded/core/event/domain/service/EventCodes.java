package com.crowded.core.event.domain.service;

import com.crowded.core.event.domain.model.EventCode;
import com.crowded.core.event.domain.repository.EventRepository;
import com.crowded.core.shared.domain.exception.ConflictException;
import java.time.Instant;
import java.util.UUID;

/**
 * One code, one upcoming event: a participant's ticket or access code must lead to a single event,
 * so a code stays free until the event that uses it ends or is cancelled.
 */
public final class EventCodes {

  private final EventRepository events;

  public EventCodes(EventRepository events) {
    this.events = events;
  }

  /**
   * Checks that no other upcoming event uses {@code code}, and holds the codes until the
   * transaction ends so that no other event takes it meanwhile.
   *
   * @param eventId the event the code is for; null for a new one
   * @throws ConflictException when another upcoming event uses it
   */
  public void checkFree(EventCode code, UUID eventId, Instant now) {
    events.lockCodes();
    if (events.isCodeUsedByAnotherUpcomingEvent(code, eventId, now)) {
      throw new ConflictException(
          "EVENT_CODE_TAKEN", "Another upcoming event uses this code. Choose another one.");
    }
  }
}
