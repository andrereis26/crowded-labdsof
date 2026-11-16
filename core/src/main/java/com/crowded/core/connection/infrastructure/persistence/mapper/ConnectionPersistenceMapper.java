package com.crowded.core.connection.infrastructure.persistence.mapper;

import com.crowded.core.connection.domain.model.ConnectionType;
import com.crowded.core.connection.domain.model.EventConnection;
import com.crowded.core.connection.infrastructure.persistence.entity.AccessCodeConnectionEntity;
import com.crowded.core.connection.infrastructure.persistence.entity.EventConnectionEntity;
import org.mapstruct.Mapper;

/** Maps connections between the domain and their rows. */
@Mapper
public interface ConnectionPersistenceMapper {

  AccessCodeConnectionEntity toAccessCodeEntity(EventConnection connection);

  /** The connection a row stores; its kind comes from the row's class. */
  default EventConnection toDomain(EventConnectionEntity entity) {
    return EventConnection.restore(
        entity.getId(),
        entity.getUserId(),
        entity.getEventId(),
        entity instanceof AccessCodeConnectionEntity
            ? ConnectionType.ACCESS_CODE
            : ConnectionType.TICKET,
        entity.getConnectedAt());
  }
}
