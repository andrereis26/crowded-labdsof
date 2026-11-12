package com.crowded.notifications.notification.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crowded.notifications.notification.api.mapper.NotificationApiMapperImpl;
import com.crowded.notifications.notification.application.dto.LiveNotification;
import com.crowded.notifications.notification.application.dto.NotificationItem;
import com.crowded.notifications.notification.application.service.LiveNotifications;
import com.crowded.notifications.notification.domain.model.NotificationType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class NotificationStreamControllerTest {

  private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");

  private final LiveNotifications live = mock(LiveNotifications.class);
  private final NotificationStreamController controller =
      new NotificationStreamController(
          live, new NotificationApiMapperImpl(), Clock.fixed(NOW, ZoneOffset.UTC));
  private final UUID user = UUID.randomUUID();

  @Test
  void streamsTheSignedInUsersNotificationsUntilTheTokenExpires() {
    when(live.subscribe(eq(user), any())).thenReturn(() -> {});

    SseEmitter emitter = controller.stream(token(NOW.plusSeconds(600)));

    assertThat(emitter.getTimeout()).isEqualTo(600_000L);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Consumer<LiveNotification>> listener = ArgumentCaptor.forClass(Consumer.class);
    verify(live).subscribe(eq(user), listener.capture());
    listener.getValue().accept(live());
    controller.heartbeat();
    controller.closeStreams();
  }

  @Test
  void aTokenWithoutExpiryKeepsTheStreamForAnHour() {
    when(live.subscribe(eq(user), any())).thenReturn(() -> {});

    assertThat(controller.stream(token(null)).getTimeout()).isEqualTo(3_600_000L);
    assertThat(controller.stream(token(NOW.minusSeconds(1))).getTimeout()).isEqualTo(1L);
  }

  private Jwt token(Instant expiresAt) {
    Jwt.Builder token = Jwt.withTokenValue("token").header("alg", "RS256").subject(user.toString());
    if (expiresAt != null) {
      token.issuedAt(NOW.minusSeconds(60)).expiresAt(expiresAt);
    }
    return token.build();
  }

  private static LiveNotification live() {
    return new LiveNotification(
        new NotificationItem(
            UUID.randomUUID(),
            NotificationType.OFFICIAL_CHANGE,
            "Adiado",
            "Nova data.",
            UUID.randomUUID(),
            "Noite de Fado",
            UUID.randomUUID(),
            NOW,
            null),
        1);
  }
}
