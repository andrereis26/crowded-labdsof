package com.crowded.core.ticket.api.controller;

import com.crowded.core.shared.api.controller.CurrentUser;
import com.crowded.core.ticket.api.dto.CompleteTicketImportRequest;
import com.crowded.core.ticket.api.dto.TicketImportResponse;
import com.crowded.core.ticket.api.mapper.TicketImportApiMapper;
import com.crowded.core.ticket.application.service.TicketImportService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Ticket import and its event-code fallback (API Outline, "Connect to an event"). */
@RestController
public class TicketImportController {

  private final TicketImportService service;
  private final TicketImportApiMapper mapper;

  public TicketImportController(TicketImportService service, TicketImportApiMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  /** Imports a ticket from its PDF for the signed-in participant. */
  @PostMapping(path = "/tickets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public TicketImportResponse importTicket(
      @RequestParam("file") MultipartFile file, @AuthenticationPrincipal Jwt token)
      throws IOException {
    return mapper.toResponse(
        service.importTicket(mapper.toCommand(CurrentUser.id(token), file.getBytes())));
  }

  /** Completes the signed-in participant's pending import with the event code they typed. */
  @PostMapping("/ticket-imports/{importId}/event-code")
  public TicketImportResponse completeImport(
      @PathVariable UUID importId,
      @Valid @RequestBody CompleteTicketImportRequest request,
      @AuthenticationPrincipal Jwt token) {
    return mapper.toResponse(
        service.completeWithEventCode(
            mapper.toCommand(CurrentUser.id(token), importId, request.eventCode())));
  }
}
