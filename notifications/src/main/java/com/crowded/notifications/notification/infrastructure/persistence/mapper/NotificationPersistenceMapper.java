package com.crowded.notifications.notification.infrastructure.persistence.mapper;

import com.crowded.notifications.notification.domain.model.Notification;
import com.crowded.notifications.notification.domain.model.NotificationText;
import com.crowded.notifications.notification.infrastructure.persistence.entity.NotificationEntity;
import org.mapstruct.Mapper;

/** Maps notifications to and from {@code notification} rows. */
@Mapper
public interface NotificationPersistenceMapper {

  NotificationEntity toEntity(Notification notification);

  default Notification toDomain(NotificationEntity row) {
    return Notification.restore(
        row.getId(),
        row.getUserId(),
        row.getEventId(),
        row.getType(),
        row.getCauseId(),
        new NotificationText(row.getTitle(), row.getBody()),
        row.getDeduplicationKey(),
        row.getCreatedAt(),
        row.getDeliveredAt(),
        row.getReadAt());
  }
}
