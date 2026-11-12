package com.crowded.core.event.infrastructure.persistence.entity;

import com.crowded.core.event.domain.model.EventStatus;
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

/** Row of {@code event}. Maps only the columns Core uses so far. */
@Entity
@Table(name = "event")
public class EventEntity {

  @Id private UUID id;

  @Column(name = "organization_id")
  private UUID organizationId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(columnDefinition = "event_status", nullable = false)
  private EventStatus status;

  @Column(name = "start_date_time")
  private Instant startDateTime;

  @Column(name = "doors_open_at")
  private Instant doorsOpenAt;

  @Column(name = "end_date_time")
  private Instant endDateTime;

  @Column(name = "venue_name")
  private String venueName;

  private String address;

  private Double latitude;

  private Double longitude;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(UUID organizationId) {
    this.organizationId = organizationId;
  }

  public EventStatus getStatus() {
    return status;
  }

  public void setStatus(EventStatus status) {
    this.status = status;
  }

  public Instant getStartDateTime() {
    return startDateTime;
  }

  public void setStartDateTime(Instant startDateTime) {
    this.startDateTime = startDateTime;
  }

  public Instant getDoorsOpenAt() {
    return doorsOpenAt;
  }

  public void setDoorsOpenAt(Instant doorsOpenAt) {
    this.doorsOpenAt = doorsOpenAt;
  }

  public Instant getEndDateTime() {
    return endDateTime;
  }

  public void setEndDateTime(Instant endDateTime) {
    this.endDateTime = endDateTime;
  }

  public String getVenueName() {
    return venueName;
  }

  public void setVenueName(String venueName) {
    this.venueName = venueName;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public Double getLatitude() {
    return latitude;
  }

  public void setLatitude(Double latitude) {
    this.latitude = latitude;
  }

  public Double getLongitude() {
    return longitude;
  }

  public void setLongitude(Double longitude) {
    this.longitude = longitude;
  }
}
