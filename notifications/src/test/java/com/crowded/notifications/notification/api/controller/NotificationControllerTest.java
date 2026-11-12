package com.crowded.notifications.notification.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crowded.notifications.notification.api.dto.NotificationListResponse;
import com.crowded.notifications.notification.api.dto.NotificationResponse;
import com.crowded.notifications.notification.api.mapper.NotificationApiMapperImpl;
import com.crowded.notifications.notification.application.dto.NotificationItem;
import com.crowded.notifications.notification.application.dto.NotificationList;
import com.crowded.notifications.notification.application.service.NotificationCenter;
import com.crowded.notifications.notification.domain.model.NotificationType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

class NotificationControllerTest {

  private final NotificationCenter center = mock(NotificationCenter.class);
  private final NotificationController controller =
      new NotificationController(center, new NotificationApiMapperImpl());
  private final UUID user = UUID.randomUUID();
  private final Jwt token =
      Jwt.withTokenValue("token").header("alg", "RS256").subject(user.toString()).build();

  @Test
  void listsTheSignedInUsersNotifications() {
    NotificationItem item =
        new NotificationItem(
            UUID.randomUUID(),
            NotificationType.OFFICIAL_CHANGE,
            "Adiado",
            "Nova data.",
            UUID.randomUUID(),
            "Noite de Fado",
            UUID.randomUUID(),
            Instant.parse("2026-11-12T10:00:00Z"),
            null);
    when(center.list(user)).thenReturn(new NotificationList(List.of(item), 1));

    NotificationListResponse response = controller.list(token);

    assertThat(response.unreadCount()).isOne();
    assertThat(response.notifications())
        .containsExactly(
            new NotificationResponse(
                item.id(),
                item.type(),
                item.title(),
                item.body(),
                item.eventId(),
                item.eventName(),
                item.causeId(),
                item.createdAt(),
                null));
  }

  @Test
  void marksTheSignedInUsersNotificationRead() {
    UUID notification = UUID.randomUUID();

    assertThat(controller.markRead(token, notification).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    verify(center).markRead(user, notification);
  }
}
