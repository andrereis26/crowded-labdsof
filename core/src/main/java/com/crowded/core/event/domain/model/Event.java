package com.crowded.core.event.domain.model;

import java.util.UUID;

/** An event, as far as official changes need it; #36 adds the rest. */
public class Event {

  private final UUID id;
  private final UUID organizationId;
  private EventState state;

  /**
   * @param organizationId the owning organization; null for an event no organization owns
   */
  public Event(UUID id, UUID organizationId, EventState state) {
    this.id = id;
    this.organizationId = organizationId;
    this.state = state;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public EventState getState() {
    return state;
  }

  /** Applies a change's outcome to the event. */
  public void apply(ChangePlan plan) {
    state = plan.after();
  }
}
