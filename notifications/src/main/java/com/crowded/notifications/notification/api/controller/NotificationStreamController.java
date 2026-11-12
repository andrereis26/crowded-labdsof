package com.crowded.notifications.notification.api.controller;

import com.crowded.notifications.notification.api.mapper.NotificationApiMapper;
import com.crowded.notifications.notification.application.dto.LiveNotification;
import com.crowded.notifications.notification.application.service.LiveNotifications;
import com.crowded.notifications.shared.api.controller.CurrentUser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * {@code GET /notifications/stream}: the signed-in user's new notifications as server-sent events
 * named {@code notification}, each with the unread count (ADR 0007). The stream ends when the token
 * expires, so a revoked or expired session stops receiving; the client reconnects with a new token
 * and fetches what it missed with {@code GET /notifications}.
 */
@RestController
public class NotificationStreamController {

  /** Comment sent on every open stream so proxies keep it open and closed ones are noticed. */
  static final long HEARTBEAT_MILLIS = 25_000;

  /** How long the client waits before reconnecting after the stream drops. */
  static final long RECONNECT_MILLIS = 5_000;

  private final LiveNotifications live;
  private final NotificationApiMapper mapper;
  private final Clock clock;
  private final Set<SseEmitter> open = ConcurrentHashMap.newKeySet();

  NotificationStreamController(LiveNotifications live, NotificationApiMapper mapper, Clock clock) {
    this.live = live;
    this.mapper = mapper;
    this.clock = clock;
  }

  @GetMapping(path = "/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(@AuthenticationPrincipal Jwt token) {
    UUID userId = CurrentUser.id(token);
    SseEmitter emitter = new SseEmitter(untilExpiry(token));
    Runnable unsubscribe = live.subscribe(userId, notification -> send(emitter, notification));
    Runnable close =
        () -> {
          unsubscribe.run();
          open.remove(emitter);
        };
    emitter.onCompletion(close);
    emitter.onError(error -> close.run());
    emitter.onTimeout(
        () -> {
          close.run();
          emitter.complete();
        });
    open.add(emitter);
    try {
      emitter.send(SseEmitter.event().reconnectTime(RECONNECT_MILLIS).comment("open"));
    } catch (IOException e) {
      emitter.completeWithError(e);
    }
    return emitter;
  }

  /** Keeps every open stream alive, and closes the ones whose client is gone. */
  @Scheduled(fixedRate = HEARTBEAT_MILLIS)
  void heartbeat() {
    for (SseEmitter emitter : open) {
      try {
        emitter.send(SseEmitter.event().comment("keep-alive"));
      } catch (IOException | IllegalStateException e) {
        open.remove(emitter);
        emitter.completeWithError(e);
      }
    }
  }

  /**
   * Ends every open stream when the service stops, before the graceful shutdown waits for open
   * requests: the clients reconnect to another instance.
   */
  @EventListener(ContextClosedEvent.class)
  void closeStreams() {
    for (SseEmitter emitter : open) {
      open.remove(emitter);
      emitter.complete();
    }
  }

  /** Milliseconds until the token expires, at least 1; the default when it has no expiry. */
  private long untilExpiry(Jwt token) {
    Instant expiresAt = token.getExpiresAt();
    if (expiresAt == null) {
      return Duration.ofHours(1).toMillis();
    }
    return Math.max(1, Duration.between(clock.instant(), expiresAt).toMillis());
  }

  private void send(SseEmitter emitter, LiveNotification notification) {
    try {
      emitter.send(
          SseEmitter.event()
              .name("notification")
              .id(notification.notification().id().toString())
              .data(mapper.toResponse(notification), MediaType.APPLICATION_JSON));
    } catch (IOException e) {
      emitter.completeWithError(e);
      throw new UncheckedIOException(e);
    }
  }
}
