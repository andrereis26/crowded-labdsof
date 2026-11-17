package com.crowded.core.event.infrastructure.persistence.entity;

import com.crowded.core.event.domain.model.CrowdLevel;
import com.crowded.core.event.domain.model.EventStatus;
import com.crowded.core.event.domain.model.EventType;
import com.crowded.core.event.domain.model.SourceType;
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

  private String name;

  private String description;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(columnDefinition = "event_type", nullable = false)
  private EventType type;

  @Column(name = "ticket_event_code")
  private String ticketEventCode;

  @Column(name = "access_code")
  private String accessCode;

  private String district;

  private Integer capacity;

  @Column(name = "venue_radius_meters")
  private int venueRadiusMeters;

  @Column(name = "high_confidence_answers")
  private int highConfidenceAnswers;

  @Column(name = "max_prompts_per_hour")
  private int maxPromptsPerHour;

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

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "crowd_level", columnDefinition = "crowd_level")
  private CrowdLevel crowdLevel;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "crowd_provenance_source_type", columnDefinition = "source_type")
  private SourceType crowdSourceType;

  @Column(name = "crowd_provenance_source_name")
  private String crowdSourceName;

  @Column(name = "crowd_provenance_last_updated_at")
  private Instant crowdLastUpdatedAt;

  @Column(name = "crowd_provenance_confirmed")
  private Boolean crowdConfirmed;

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

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public EventType getType() {
    return type;
  }

  public void setType(EventType type) {
    this.type = type;
  }

  public String getTicketEventCode() {
    return ticketEventCode;
  }

  public void setTicketEventCode(String ticketEventCode) {
    this.ticketEventCode = ticketEventCode;
  }

  public String getAccessCode() {
    return accessCode;
  }

  public void setAccessCode(String accessCode) {
    this.accessCode = accessCode;
  }

  public String getDistrict() {
    return district;
  }

  public void setDistrict(String district) {
    this.district = district;
  }

  public Integer getCapacity() {
    return capacity;
  }

  public void setCapacity(Integer capacity) {
    this.capacity = capacity;
  }

  public int getVenueRadiusMeters() {
    return venueRadiusMeters;
  }

  public void setVenueRadiusMeters(int venueRadiusMeters) {
    this.venueRadiusMeters = venueRadiusMeters;
  }

  public int getHighConfidenceAnswers() {
    return highConfidenceAnswers;
  }

  public void setHighConfidenceAnswers(int highConfidenceAnswers) {
    this.highConfidenceAnswers = highConfidenceAnswers;
  }

  public int getMaxPromptsPerHour() {
    return maxPromptsPerHour;
  }

  public void setMaxPromptsPerHour(int maxPromptsPerHour) {
    this.maxPromptsPerHour = maxPromptsPerHour;
  }

  public CrowdLevel getCrowdLevel() {
    return crowdLevel;
  }

  public void setCrowdLevel(CrowdLevel crowdLevel) {
    this.crowdLevel = crowdLevel;
  }

  public SourceType getCrowdSourceType() {
    return crowdSourceType;
  }

  public void setCrowdSourceType(SourceType crowdSourceType) {
    this.crowdSourceType = crowdSourceType;
  }

  public String getCrowdSourceName() {
    return crowdSourceName;
  }

  public void setCrowdSourceName(String crowdSourceName) {
    this.crowdSourceName = crowdSourceName;
  }

  public Instant getCrowdLastUpdatedAt() {
    return crowdLastUpdatedAt;
  }

  public void setCrowdLastUpdatedAt(Instant crowdLastUpdatedAt) {
    this.crowdLastUpdatedAt = crowdLastUpdatedAt;
  }

  public Boolean getCrowdConfirmed() {
    return crowdConfirmed;
  }

  public void setCrowdConfirmed(Boolean crowdConfirmed) {
    this.crowdConfirmed = crowdConfirmed;
  }
}
