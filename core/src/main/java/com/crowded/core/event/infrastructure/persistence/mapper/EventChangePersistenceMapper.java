package com.crowded.core.event.infrastructure.persistence.mapper;

import com.crowded.core.event.domain.model.EventChange;
import com.crowded.core.event.domain.model.Provenance;
import com.crowded.core.event.infrastructure.persistence.entity.EventChangeEntity;
import com.crowded.core.shared.infrastructure.persistence.JsonColumnConverter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps changes to and from {@code event_change} rows; the changed values are JSON. */
@Mapper(uses = JsonColumnConverter.class)
public interface EventChangePersistenceMapper {

  @Mapping(target = "provenanceSourceType", source = "provenance.sourceType")
  @Mapping(target = "provenanceSourceName", source = "provenance.sourceName")
  @Mapping(target = "provenanceLastUpdatedAt", source = "provenance.lastUpdatedAt")
  @Mapping(target = "provenanceConfirmed", source = "provenance.confirmed")
  EventChangeEntity toEntity(EventChange change);

  /** The change in {@code entity}, with its JSON columns read by {@code json}. */
  default EventChange toDomain(EventChangeEntity entity, JsonColumnConverter json) {
    return EventChange.restore(
        entity.getId(),
        entity.getEventId(),
        entity.getType(),
        entity.getSeverity(),
        json.toMap(entity.getPreviousValue()),
        json.toMap(entity.getNewValue()),
        entity.getTicketStillValid(),
        entity.getRefundInfo(),
        entity.getMessage(),
        new Provenance(
            entity.getProvenanceSourceType(),
            entity.getProvenanceSourceName(),
            entity.getProvenanceLastUpdatedAt(),
            entity.isProvenanceConfirmed()));
  }
}
