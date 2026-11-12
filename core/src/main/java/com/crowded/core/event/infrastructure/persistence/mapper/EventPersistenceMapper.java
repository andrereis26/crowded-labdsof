package com.crowded.core.event.infrastructure.persistence.mapper;

import com.crowded.core.event.domain.model.Coordinates;
import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.EventState;
import com.crowded.core.event.domain.model.Schedule;
import com.crowded.core.event.domain.model.Venue;
import com.crowded.core.event.infrastructure.persistence.entity.EventEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/** Maps {@code event} rows to events, and an event's state back onto its row. */
@Mapper
public interface EventPersistenceMapper {

  default Event toDomain(EventEntity entity) {
    return Event.restore(
        entity.getId(),
        entity.getOrganizationId(),
        new EventState(
            entity.getStatus(),
            new Schedule(
                entity.getStartDateTime(), entity.getDoorsOpenAt(), entity.getEndDateTime()),
            new Venue(
                entity.getVenueName(),
                entity.getAddress(),
                Coordinates.ofNullable(entity.getLatitude(), entity.getLongitude()))));
  }

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "organizationId", ignore = true)
  @Mapping(target = "status", source = "state.status")
  @Mapping(target = "startDateTime", source = "state.schedule.start")
  @Mapping(target = "doorsOpenAt", source = "state.schedule.doorsOpenAt")
  @Mapping(target = "endDateTime", source = "state.schedule.end")
  @Mapping(target = "venueName", source = "state.venue.name")
  @Mapping(target = "address", source = "state.venue.address")
  @Mapping(target = "latitude", source = "state.venue.coordinates.latitude")
  @Mapping(target = "longitude", source = "state.venue.coordinates.longitude")
  void updateEntity(Event event, @MappingTarget EventEntity entity);
}
