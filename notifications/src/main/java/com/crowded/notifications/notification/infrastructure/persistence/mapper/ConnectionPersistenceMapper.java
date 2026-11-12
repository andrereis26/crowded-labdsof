package com.crowded.notifications.notification.infrastructure.persistence.mapper;

import com.crowded.notifications.notification.domain.model.Connection;
import com.crowded.notifications.notification.infrastructure.persistence.repository.ConnectionRow;
import java.util.List;
import org.mapstruct.Mapper;

/** Maps the connections read from Core's tables. */
@Mapper
public interface ConnectionPersistenceMapper {

  Connection toDomain(ConnectionRow row);

  List<Connection> toDomain(List<ConnectionRow> rows);
}
