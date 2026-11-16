package com.crowded.core.connection.infrastructure.persistence.mapper;

import com.crowded.core.connection.domain.model.EventConnection;
import com.crowded.core.connection.infrastructure.persistence.entity.AccessCodeConnectionEntity;
import com.crowded.core.connection.infrastructure.persistence.entity.EventConnectionEntity;
import java.util.Objects;
import org.mapstruct.Mapper;

/** Maps connections between the domain and their rows. */
@Mapper
public interface ConnectionPersistenceMapper {

  AccessCodeConnectionEntity toAccessCodeEntity(EventConnection connection);

  /** The connection a row stores; a stored row always has its id. */
  default EventConnection toDomain(EventConnectionEntity entity) {
    return EventConnection.restore(
        Objects.requireNonNull(entity.getId(), "id"),
        entity.getUserId(),
        entity.getEventId(),
        entity.getConnectedAt());
  }
}
