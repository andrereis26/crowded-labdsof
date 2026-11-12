package com.crowded.notifications.notification.api.mapper;

import com.crowded.notifications.notification.api.dto.LiveNotificationResponse;
import com.crowded.notifications.notification.api.dto.NotificationListResponse;
import com.crowded.notifications.notification.api.dto.NotificationResponse;
import com.crowded.notifications.notification.application.dto.LiveNotification;
import com.crowded.notifications.notification.application.dto.NotificationItem;
import com.crowded.notifications.notification.application.dto.NotificationList;
import org.mapstruct.Mapper;

/** Maps the notification center and the live stream to their responses. */
@Mapper
public interface NotificationApiMapper {

  NotificationResponse toResponse(NotificationItem item);

  NotificationListResponse toResponse(NotificationList list);

  LiveNotificationResponse toResponse(LiveNotification live);
}
