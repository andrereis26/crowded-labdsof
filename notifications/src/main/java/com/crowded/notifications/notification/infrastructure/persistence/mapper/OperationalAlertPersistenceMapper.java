package com.crowded.notifications.notification.infrastructure.persistence.mapper;

import com.crowded.notifications.notification.domain.model.AlertType;
import com.crowded.notifications.notification.domain.model.OperationalAlert;
import com.crowded.notifications.notification.infrastructure.persistence.entity.AlertEntity;
import org.mapstruct.Mapper;

/** Maps the operational alerts read from Core's {@code alert}. */
@Mapper
public interface OperationalAlertPersistenceMapper {

  default OperationalAlert toDomain(AlertEntity entity) {
    return OperationalAlert.restore(
        entity.getId(),
        entity.getEventId(),
        AlertType.valueOf(entity.getType()),
        entity.getMessage());
  }
}
