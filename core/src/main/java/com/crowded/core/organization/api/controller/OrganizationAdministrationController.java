package com.crowded.core.organization.api.controller;

import com.crowded.core.organization.api.dto.CreateOrganizationRequest;
import com.crowded.core.organization.api.dto.CreatedOrganizationResponse;
import com.crowded.core.organization.api.dto.OrganizationResponse;
import com.crowded.core.organization.api.mapper.OrganizationAdministrationApiMapper;
import com.crowded.core.organization.application.service.OrganizationAdministrationService;
import com.crowded.core.shared.api.controller.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Organizations, for a platform administrator (API Outline, Administration). */
@RestController
public class OrganizationAdministrationController {

  private final OrganizationAdministrationService service;
  private final OrganizationAdministrationApiMapper mapper;

  OrganizationAdministrationController(
      OrganizationAdministrationService service, OrganizationAdministrationApiMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  /** Every organization with its number of events and its members. */
  @GetMapping("/admin/organizations")
  public List<OrganizationResponse> list(@AuthenticationPrincipal Jwt token) {
    return mapper.toResponses(service.list(CurrentUser.id(token)));
  }

  /** Creates an organization: 201 with its id. */
  @PostMapping("/admin/organizations")
  @ResponseStatus(HttpStatus.CREATED)
  public CreatedOrganizationResponse create(
      @AuthenticationPrincipal Jwt token, @Valid @RequestBody CreateOrganizationRequest request) {
    return new CreatedOrganizationResponse(
        service.create(mapper.toCommand(CurrentUser.id(token), request)));
  }
}
