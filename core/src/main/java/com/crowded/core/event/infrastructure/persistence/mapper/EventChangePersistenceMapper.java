package com.crowded.core.event.infrastructure.persistence.mapper;

import com.crowded.core.event.domain.model.EventChange;
import com.crowded.core.event.infrastructure.persistence.entity.EventChangeEntity;
import com.crowded.core.shared.infrastructure.persistence.JsonColumnConverter;
import org.mapstruct.Mapper;

/** Maps changes to and from {@code event_change} rows; the changed values are JSON. */
@Mapper(uses = JsonColumnConverter.class)
public interface EventChangePersistenceMapper {

  EventChangeEntity toEntity(EventChange change);

  EventChange toDomain(EventChangeEntity entity);
}
