package com.crowded.core.event.api.controller;

import com.crowded.core.event.api.dto.CrowdResponse;
import com.crowded.core.event.api.dto.PublishCrowdLevelRequest;
import com.crowded.core.event.api.mapper.CrowdLevelApiMapper;
import com.crowded.core.event.application.service.CrowdLevelService;
import com.crowded.core.shared.api.controller.CurrentUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The crowd level an organization publishes during its event (API Outline, journey 5). */
@RestController
public class CrowdLevelController {

  private final CrowdLevelService service;
  private final CrowdLevelApiMapper mapper;

  CrowdLevelController(CrowdLevelService service, CrowdLevelApiMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @PutMapping("/events/{eventId}/crowd")
  public CrowdResponse publish(
      @PathVariable UUID eventId,
      @AuthenticationPrincipal Jwt token,
      @Valid @RequestBody PublishCrowdLevelRequest request) {
    return mapper.toResponse(
        service.publish(mapper.toCommand(eventId, CurrentUser.id(token), request)));
  }
}
