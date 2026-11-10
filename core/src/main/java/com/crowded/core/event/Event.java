package com.crowded.core.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** An event. Maps only the columns Core uses so far; #36 adds the rest. */
@Entity
@Table(name = "event")
public class Event {

  @Id private UUID id;

  @Column(name = "organization_id")
  private UUID organizationId;

  @Column(name = "start_date_time")
  private Instant startDateTime;

  @Column(name = "doors_open_at")
  private Instant doorsOpenAt;

  @Column(name = "end_date_time")
  private Instant endDateTime;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(columnDefinition = "event_status", nullable = false)
  private EventStatus status;

  @Column(name = "venue_name")
  private String venueName;

  private String address;

  private Double latitude;

  private Double longitude;

  protected Event() {}

  /** An event in the given state, for tests. */
  Event(UUID id, UUID organizationId, EventState state) {
    this.id = id;
    this.organizationId = organizationId;
    moveTo(state);
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  EventState state() {
    return new EventState(
        status, startDateTime, doorsOpenAt, endDateTime, venueName, address, latitude, longitude);
  }

  final void moveTo(EventState state) {
    status = state.status();
    startDateTime = state.startDateTime();
    doorsOpenAt = state.doorsOpenAt();
    endDateTime = state.endDateTime();
    venueName = state.venueName();
    address = state.address();
    latitude = state.latitude();
    longitude = state.longitude();
  }
}
