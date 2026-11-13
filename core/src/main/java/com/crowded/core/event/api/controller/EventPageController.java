package com.crowded.core.event.api.controller;

import com.crowded.core.event.api.dto.EventChangeResponse;
import com.crowded.core.event.api.dto.EventPageResponse;
import com.crowded.core.event.api.mapper.EventPageApiMapper;
import com.crowded.core.event.application.dto.EventPage;
import com.crowded.core.event.application.service.EventViewService;
import com.crowded.core.event.application.service.LiveEventPages;
import com.crowded.core.shared.api.controller.CurrentUser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * An event as its connected participants and its organization read it: the page, its change history
 * and the page's live updates (API Outline, journeys 3 and 5).
 */
@RestController
public class EventPageController {

  /** Comment sent on every open stream so proxies keep it open and closed ones are noticed. */
  static final long HEARTBEAT_MILLIS = 25_000;

  /** How long the client waits before reconnecting after the stream drops. */
  static final long RECONNECT_MILLIS = 5_000;

  private final EventViewService service;
  private final LiveEventPages live;
  private final EventPageApiMapper mapper;
  private final Clock clock;
  private final Set<SseEmitter> open = ConcurrentHashMap.newKeySet();

  EventPageController(
      EventViewService service, LiveEventPages live, EventPageApiMapper mapper, Clock clock) {
    this.service = service;
    this.live = live;
    this.mapper = mapper;
    this.clock = clock;
  }

  @GetMapping("/events/{eventId}")
  public EventPageResponse page(@PathVariable UUID eventId, @AuthenticationPrincipal Jwt token) {
    return mapper.toResponse(service.page(CurrentUser.id(token), eventId));
  }

  /** The official changes, the newest first; an empty list when there are none. */
  @GetMapping("/events/{eventId}/changes")
  public List<EventChangeResponse> history(
      @PathVariable UUID eventId, @AuthenticationPrincipal Jwt token) {
    return service.history(CurrentUser.id(token), eventId).stream()
        .map(mapper::toResponse)
        .toList();
  }

  /**
   * The page as server-sent events named {@code event}: the current page first, then the new one
   * each time it changes (ADR 0007). The stream ends when the token expires; the client reconnects
   * with a new token.
   */
  @GetMapping(path = "/events/{eventId}/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter live(@PathVariable UUID eventId, @AuthenticationPrincipal Jwt token) {
    EventPage current = service.page(CurrentUser.id(token), eventId);
    SseEmitter emitter = new SseEmitter(untilExpiry(token));
    Runnable unsubscribe = live.subscribe(eventId, page -> send(emitter, page));
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
      send(emitter, current);
    } catch (IOException | UncheckedIOException e) {
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

  /** Milliseconds until the token expires, at least 1; an hour when it has no expiry. */
  private long untilExpiry(Jwt token) {
    Instant expiresAt = token.getExpiresAt();
    if (expiresAt == null) {
      return Duration.ofHours(1).toMillis();
    }
    return Math.max(1, Duration.between(clock.instant(), expiresAt).toMillis());
  }

  private void send(SseEmitter emitter, EventPage page) {
    try {
      emitter.send(
          SseEmitter.event()
              .name("event")
              .data(mapper.toResponse(page), MediaType.APPLICATION_JSON));
    } catch (IOException e) {
      emitter.completeWithError(e);
      throw new UncheckedIOException(e);
    }
  }
}
