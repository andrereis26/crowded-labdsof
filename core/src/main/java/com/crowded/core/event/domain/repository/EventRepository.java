package com.crowded.core.event.domain.repository;

import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.EventCode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Events. */
public interface EventRepository {

  /**
   * The event, locked until the transaction ends, so two changes to one event are decided one after
   * the other and a resent change finds the first one applied.
   */
  Optional<Event> findByIdForUpdate(UUID id);

  /** The event, to read it. */
  Optional<Event> findById(UUID id);

  /** The organization's events, the latest start first. */
  List<Event> findByOrganization(UUID organizationId);

  /** Stores a new event. */
  void add(Event event);

  /** Stores the event's new state. */
  void save(Event event);

  /** How many events each organization has; an organization without events is not in the map. */
  Map<UUID, Long> countByOrganization();

  /**
   * Holds the event codes until the transaction ends, so two events saved at once cannot take the
   * same code.
   */
  void lockCodes();

  /**
   * Whether an event other than {@code eventId} that has not ended or been cancelled by {@code now}
   * uses {@code code}, on its tickets or as its access code.
   *
   * @param eventId the event the code is for; null for a new one
   */
  boolean isCodeUsedByAnotherUpcomingEvent(EventCode code, UUID eventId, Instant now);

  /**
   * The public event that has not ended or been cancelled by {@code now} and gives out {@code code}
   * as its access code; empty when there is none. Codes are unique among such events.
   */
  Optional<Event> findUpcomingPublicByAccessCode(EventCode code, Instant now);

  /**
   * The ticketed event that has not ended or been cancelled by {@code now} and has {@code code}
   * printed on its tickets; empty when there is none. Codes are unique among such events.
   */
  Optional<Event> findUpcomingTicketedByEventCode(EventCode code, Instant now);
}
