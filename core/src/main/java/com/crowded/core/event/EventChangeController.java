package com.crowded.core.event;

import com.crowded.core.web.ApiException;
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
class EventChangeController {

  private final EventChangeService service;

  EventChangeController(EventChangeService service) {
    this.service = service;
  }

  /**
   * Publishes a change: 201 with the new change or, for a change the event already has, 200 with
   * the one recorded the first time.
   */
  @PostMapping("/events/{eventId}/changes")
  ResponseEntity<ChangeResponse> publish(
      @PathVariable UUID eventId,
      @AuthenticationPrincipal Jwt token,
      @Valid @RequestBody ChangeRequest request) {
    PublishedChange change = service.publish(eventId, userId(token), request);
    return ResponseEntity.status(change.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(new ChangeResponse(change.changeId(), change.severity()));
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
    throw new ApiException(
        HttpStatus.FORBIDDEN, "NOT_EVENT_ORGANIZATION", "The token names no user.");
  }

  /** Response of a published change. */
  record ChangeResponse(UUID changeId, Severity severity) {}
}
