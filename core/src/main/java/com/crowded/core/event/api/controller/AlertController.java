package com.crowded.core.event.api.controller;

import com.crowded.core.event.api.dto.SendAlertRequest;
import com.crowded.core.event.api.dto.SentAlertResponse;
import com.crowded.core.event.api.mapper.AlertApiMapper;
import com.crowded.core.event.application.service.AlertService;
import com.crowded.core.shared.api.controller.CurrentUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The operational alerts an organization sends during its event (API Outline, journey 5). */
@RestController
public class AlertController {

  private final AlertService service;
  private final AlertApiMapper mapper;

  AlertController(AlertService service, AlertApiMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @PostMapping("/events/{eventId}/alerts")
  @ResponseStatus(HttpStatus.CREATED)
  public SentAlertResponse send(
      @PathVariable UUID eventId,
      @AuthenticationPrincipal Jwt token,
      @Valid @RequestBody SendAlertRequest request) {
    return mapper.toResponse(
        service.send(mapper.toCommand(eventId, CurrentUser.id(token), request)));
  }
}
