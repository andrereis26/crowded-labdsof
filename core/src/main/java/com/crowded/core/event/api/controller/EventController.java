package com.crowded.core.event.api.controller;

import com.crowded.core.event.api.dto.CorrectEventRequest;
import com.crowded.core.event.api.dto.CreateEventRequest;
import com.crowded.core.event.api.dto.ManagedEventResponse;
import com.crowded.core.event.api.mapper.EventApiMapper;
import com.crowded.core.event.application.dto.ManagedEvent;
import com.crowded.core.event.application.service.EventManagementService;
import com.crowded.core.shared.api.controller.CurrentUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** An organization's events in the backoffice (API Outline, journey 1). */
@RestController
public class EventController {

  private final EventManagementService service;
  private final EventApiMapper mapper;

  EventController(EventManagementService service, EventApiMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  /** The events of the signed-in member's organization, the latest start first. */
  @GetMapping("/organizations/me/events")
  public List<ManagedEventResponse> mine(@AuthenticationPrincipal Jwt token) {
    return service.listForMember(CurrentUser.id(token)).stream().map(mapper::toResponse).toList();
  }

  /** Registers and publishes an event of the member's organization: 201 with the new event. */
  @PostMapping("/events")
  public ResponseEntity<ManagedEventResponse> create(
      @AuthenticationPrincipal Jwt token, @Valid @RequestBody CreateEventRequest request) {
    ManagedEvent created = service.register(mapper.toCommand(CurrentUser.id(token), request));
    return ResponseEntity.created(URI.create("/events/" + created.event().getId()))
        .body(mapper.toResponse(created));
  }

  /** Corrects details that are not an official change: 200 with the event as it is now. */
  @PatchMapping("/events/{eventId}")
  public ManagedEventResponse correct(
      @PathVariable UUID eventId,
      @AuthenticationPrincipal Jwt token,
      @Valid @RequestBody CorrectEventRequest request) {
    return mapper.toResponse(
        service.correct(mapper.toCommand(CurrentUser.id(token), eventId, request)));
  }

  /** Deletes an event nobody is connected to, such as one created by mistake: 204. */
  @DeleteMapping("/events/{eventId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID eventId, @AuthenticationPrincipal Jwt token) {
    service.delete(CurrentUser.id(token), eventId);
  }
}
