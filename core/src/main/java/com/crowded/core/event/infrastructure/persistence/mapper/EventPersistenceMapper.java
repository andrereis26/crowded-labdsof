package com.crowded.core.event.infrastructure.persistence.mapper;

import com.crowded.core.event.domain.model.Admission;
import com.crowded.core.event.domain.model.Capacity;
import com.crowded.core.event.domain.model.ConfirmationRules;
import com.crowded.core.event.domain.model.ConfirmationSettings;
import com.crowded.core.event.domain.model.Coordinates;
import com.crowded.core.event.domain.model.CrowdStatus;
import com.crowded.core.event.domain.model.District;
import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.EventCode;
import com.crowded.core.event.domain.model.EventProfile;
import com.crowded.core.event.domain.model.EventState;
import com.crowded.core.event.domain.model.EventType;
import com.crowded.core.event.domain.model.Provenance;
import com.crowded.core.event.domain.model.QueuePoint;
import com.crowded.core.event.domain.model.QueuePoints;
import com.crowded.core.event.domain.model.QueueTime;
import com.crowded.core.event.domain.model.ReportCategory;
import com.crowded.core.event.domain.model.Schedule;
import com.crowded.core.event.domain.model.Venue;
import com.crowded.core.event.infrastructure.persistence.entity.EventCategorySettingEntity;
import com.crowded.core.event.infrastructure.persistence.entity.EventEntity;
import com.crowded.core.event.infrastructure.persistence.entity.QueuePointEntity;
import com.crowded.core.event.infrastructure.persistence.entity.QueueTimeEntity;
import com.crowded.core.event.infrastructure.persistence.entity.ReportCategoryEntity;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Maps {@code event} rows, with their queue points, waiting times and category settings, to events,
 * and an event's values back onto its rows.
 */
@Mapper
public interface EventPersistenceMapper {

  /**
   * The event of {@code entity}.
   *
   * @param points the event's queue points, in order
   * @param timesByPoint the waiting times of the event's points, by point id
   * @param settings the event's own category settings; a category without one keeps its default
   * @param catalog every report category
   */
  default Event toDomain(
      EventEntity entity,
      List<QueuePointEntity> points,
      Map<UUID, QueueTimeEntity> timesByPoint,
      Collection<EventCategorySettingEntity> settings,
      Collection<ReportCategory> catalog) {
    String code =
        entity.getType() == EventType.TICKETED
            ? entity.getTicketEventCode()
            : entity.getAccessCode();
    return Event.restore(
        entity.getId(),
        entity.getOrganizationId(),
        new EventProfile(entity.getName(), entity.getDescription()),
        new Admission(entity.getType(), new EventCode(code)),
        new EventState(
            entity.getStatus(),
            new Schedule(
                entity.getStartDateTime(), entity.getDoorsOpenAt(), entity.getEndDateTime()),
            new Venue(
                entity.getVenueName(),
                entity.getAddress(),
                Coordinates.ofNullable(entity.getLatitude(), entity.getLongitude()))),
        new District(entity.getDistrict()),
        entity.getCapacity() == null ? null : new Capacity(entity.getCapacity()),
        new QueuePoints(
            points.stream()
                .map(p -> new QueuePoint(p.getName(), queueTime(timesByPoint.get(p.getId()))))
                .toList()),
        new ConfirmationSettings(
            promptCategories(settings, catalog),
            new ConfirmationRules(
                entity.getVenueRadiusMeters(),
                entity.getHighConfidenceAnswers(),
                entity.getMaxPromptsPerHour())),
        crowd(entity));
  }

  /** The published crowd level; null when the event has none. */
  private static CrowdStatus crowd(EventEntity entity) {
    if (entity.getCrowdLevel() == null) {
      return null;
    }
    return new CrowdStatus(
        entity.getCrowdLevel(),
        new Provenance(
            entity.getCrowdSourceType(),
            entity.getCrowdSourceName(),
            entity.getCrowdLastUpdatedAt(),
            Boolean.TRUE.equals(entity.getCrowdConfirmed())));
  }

  /** The published waiting time; null when the point has none. */
  private static QueueTime queueTime(QueueTimeEntity entity) {
    if (entity == null) {
      return null;
    }
    return new QueueTime(
        entity.getWaitMinutes(),
        new Provenance(
            entity.getProvenanceSourceType(),
            entity.getProvenanceSourceName(),
            entity.getProvenanceLastUpdatedAt(),
            entity.isProvenanceConfirmed()));
  }

  /** The categories that ask for confirmations: the event's choice, or the category's default. */
  private static Set<String> promptCategories(
      Collection<EventCategorySettingEntity> settings, Collection<ReportCategory> catalog) {
    Map<String, Boolean> chosen =
        settings.stream()
            .collect(
                Collectors.toMap(
                    EventCategorySettingEntity::getReportCategoryCode,
                    EventCategorySettingEntity::isPromptsEnabled));
    Set<String> codes = new HashSet<>();
    for (ReportCategory category : catalog) {
      if (!category.personal()
          && chosen.getOrDefault(category.code(), category.promptsEnabledByDefault())) {
        codes.add(category.code());
      }
    }
    return codes;
  }

  ReportCategory toDomain(ReportCategoryEntity entity);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "organizationId", ignore = true)
  @Mapping(target = "name", source = "profile.name")
  @Mapping(target = "description", source = "profile.description")
  @Mapping(target = "type", source = "admission.type")
  @Mapping(target = "ticketEventCode", expression = "java(event.getAdmission().ticketEventCode())")
  @Mapping(target = "accessCode", expression = "java(event.getAdmission().accessCode())")
  @Mapping(target = "status", source = "state.status")
  @Mapping(target = "startDateTime", source = "state.schedule.start")
  @Mapping(target = "doorsOpenAt", source = "state.schedule.doorsOpenAt")
  @Mapping(target = "endDateTime", source = "state.schedule.end")
  @Mapping(target = "venueName", source = "state.venue.name")
  @Mapping(target = "address", source = "state.venue.address")
  @Mapping(target = "latitude", source = "state.venue.coordinates.latitude")
  @Mapping(target = "longitude", source = "state.venue.coordinates.longitude")
  @Mapping(target = "district", source = "district.name")
  @Mapping(target = "capacity", source = "capacity.people")
  @Mapping(target = "venueRadiusMeters", source = "confirmations.rules.venueRadiusMeters")
  @Mapping(target = "highConfidenceAnswers", source = "confirmations.rules.highConfidenceAnswers")
  @Mapping(target = "maxPromptsPerHour", source = "confirmations.rules.maxPromptsPerHour")
  @Mapping(target = "crowdLevel", source = "crowd.level")
  @Mapping(target = "crowdSourceType", source = "crowd.provenance.sourceType")
  @Mapping(target = "crowdSourceName", source = "crowd.provenance.sourceName")
  @Mapping(target = "crowdLastUpdatedAt", source = "crowd.provenance.lastUpdatedAt")
  @Mapping(
      target = "crowdConfirmed",
      expression =
          "java(event.getCrowd() == null ? null : event.getCrowd().provenance().confirmed())")
  void updateEntity(Event event, @MappingTarget EventEntity entity);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "eventId", ignore = true)
  @Mapping(target = "queuePointId", ignore = true)
  @Mapping(target = "provenanceSourceType", source = "provenance.sourceType")
  @Mapping(target = "provenanceSourceName", source = "provenance.sourceName")
  @Mapping(target = "provenanceLastUpdatedAt", source = "provenance.lastUpdatedAt")
  @Mapping(target = "provenanceConfirmed", source = "provenance.confirmed")
  void updateEntity(QueueTime time, @MappingTarget QueueTimeEntity entity);
}
