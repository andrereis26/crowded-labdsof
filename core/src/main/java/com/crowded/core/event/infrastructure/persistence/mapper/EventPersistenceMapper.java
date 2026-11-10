package com.crowded.core.event.infrastructure.persistence.mapper;

import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.EventState;
import com.crowded.core.event.infrastructure.persistence.entity.EventEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/** Maps {@code event} rows to events, and an event's new state back to its row. */
@Mapper
public interface EventPersistenceMapper {

  @Mapping(target = "state", source = "entity")
  Event toDomain(EventEntity entity);

  EventState toState(EventEntity entity);

  /** Copies the event's state to its row; the id and owner never change. */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "organizationId", ignore = true)
  @Mapping(target = "status", source = "state.status")
  @Mapping(target = "startDateTime", source = "state.startDateTime")
  @Mapping(target = "doorsOpenAt", source = "state.doorsOpenAt")
  @Mapping(target = "endDateTime", source = "state.endDateTime")
  @Mapping(target = "venueName", source = "state.venueName")
  @Mapping(target = "address", source = "state.address")
  @Mapping(target = "latitude", source = "state.latitude")
  @Mapping(target = "longitude", source = "state.longitude")
  void updateEntity(Event event, @MappingTarget EventEntity entity);
}
