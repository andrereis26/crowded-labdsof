package com.crowded.notifications.notification.infrastructure.persistence.mapper;

import com.crowded.notifications.notification.domain.model.Notification;
import com.crowded.notifications.notification.infrastructure.persistence.entity.NotificationEntity;
import org.mapstruct.Mapper;

/** Maps notifications to {@code notification} rows. */
@Mapper
public interface NotificationPersistenceMapper {

  NotificationEntity toEntity(Notification notification);
}
