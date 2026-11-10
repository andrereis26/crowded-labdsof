package com.crowded.core.event.api.controller;

import com.crowded.core.event.api.dto.PublishChangeRequest;
import com.crowded.core.event.api.dto.PublishedChangeResponse;
import com.crowded.core.event.api.mapper.EventChangeApiMapper;
import com.crowded.core.event.application.dto.PublishedChange;
import com.crowded.core.event.application.service.EventChangeService;
import com.crowded.core.shared.domain.exception.NotAllowedException;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Official changes to an event (API Outline, journey 3). */
@RestController
public class EventChangeController {

  private final EventChangeService service;
  private final EventChangeApiMapper mapper;

  public EventChangeController(EventChangeService service, EventChangeApiMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  /**
   * Publishes a change: 201 with the new change or, for a change the event already has, 200 with
   * the one recorded the first time.
   */
  @PostMapping("/events/{eventId}/changes")
  public ResponseEntity<PublishedChangeResponse> publish(
      @PathVariable UUID eventId,
      @AuthenticationPrincipal Jwt token,
      @Valid @RequestBody PublishChangeRequest request) {
    PublishedChange change = service.publish(mapper.toCommand(eventId, userId(token), request));
    return ResponseEntity.status(change.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(mapper.toResponse(change));
  }

  /** The user is the token's subject; Core reads their role and organization (SR-001). */
  private static UUID userId(Jwt token) {
    String subject = token.getSubject();
    try {
      if (subject != null) {
        return UUID.fromString(subject);
      }
    } catch (IllegalArgumentException e) {
      // Not a user id: refused below.
    }
    throw new NotAllowedException("NOT_EVENT_ORGANIZATION", "The token names no user.");
  }
}
