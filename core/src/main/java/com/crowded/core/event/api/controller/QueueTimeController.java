package com.crowded.core.event.api.controller;

import com.crowded.core.event.api.dto.PublishQueueTimesRequest;
import com.crowded.core.event.api.dto.QueueTimesResponse;
import com.crowded.core.event.api.mapper.QueueTimeApiMapper;
import com.crowded.core.event.application.service.QueueTimeService;
import com.crowded.core.shared.api.controller.CurrentUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The queue waiting times an organization publishes during its event (API Outline, journey 5). */
@RestController
public class QueueTimeController {

  private final QueueTimeService service;
  private final QueueTimeApiMapper mapper;

  QueueTimeController(QueueTimeService service, QueueTimeApiMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @PutMapping("/events/{eventId}/queues")
  public QueueTimesResponse publish(
      @PathVariable UUID eventId,
      @AuthenticationPrincipal Jwt token,
      @Valid @RequestBody PublishQueueTimesRequest request) {
    return mapper.toResponse(
        service.publish(mapper.toCommand(eventId, CurrentUser.id(token), request)));
  }
}
