package com.crowded.notifications.notification.api.controller;

import com.crowded.notifications.notification.api.dto.NotificationListResponse;
import com.crowded.notifications.notification.api.mapper.NotificationApiMapper;
import com.crowded.notifications.notification.application.service.NotificationCenter;
import com.crowded.notifications.shared.api.controller.CurrentUser;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's notification center (API Outline, Notification center). */
@RestController
public class NotificationController {

  private final NotificationCenter center;
  private final NotificationApiMapper mapper;

  NotificationController(NotificationCenter center, NotificationApiMapper mapper) {
    this.center = center;
    this.mapper = mapper;
  }

  /** The user's notifications, newest first, and how many are unread. */
  @GetMapping("/notifications")
  public NotificationListResponse list(@AuthenticationPrincipal Jwt token) {
    return mapper.toResponse(center.list(CurrentUser.id(token)));
  }

  /** Marks one of the user's notifications read: 204, also when it was read before. */
  @PostMapping("/notifications/{notificationId}/read")
  public ResponseEntity<Void> markRead(
      @AuthenticationPrincipal Jwt token, @PathVariable UUID notificationId) {
    center.markRead(CurrentUser.id(token), notificationId);
    return ResponseEntity.noContent().build();
  }
}
