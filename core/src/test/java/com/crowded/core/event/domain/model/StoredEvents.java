package com.crowded.core.event.domain.model;

import java.util.Set;
import java.util.UUID;

/** Stored events for tests that only care about their organization and state. */
public final class StoredEvents {

  private StoredEvents() {}

  /** A ticketed event in Porto with {@code state}, as if read from the database. */
  public static Event restore(UUID id, UUID organizationId, EventState state) {
    return Event.restore(
        id,
        organizationId,
        new EventProfile("Concerto", "Um concerto no Porto."),
        new Admission(EventType.TICKETED, new EventCode("CONC-2026")),
        state,
        new District("Porto"),
        new Capacity(3000),
        QueuePoints.NONE,
        new ConfirmationSettings(Set.of("CROWD_LEVEL"), ConfirmationRules.DEFAULT),
        null);
  }
}
