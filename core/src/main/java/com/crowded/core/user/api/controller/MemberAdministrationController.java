package com.crowded.core.user.api.controller;

import com.crowded.core.shared.api.controller.CurrentUser;
import com.crowded.core.user.api.dto.AddMemberRequest;
import com.crowded.core.user.api.dto.ChangeMemberRequest;
import com.crowded.core.user.api.dto.MemberChangedResponse;
import com.crowded.core.user.api.dto.TemporaryPasswordResponse;
import com.crowded.core.user.api.mapper.MemberAdministrationApiMapper;
import com.crowded.core.user.application.service.MemberAdministrationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Organization members, for a platform administrator (API Outline, Administration). */
@RestController
public class MemberAdministrationController {

  private final MemberAdministrationService service;
  private final MemberAdministrationApiMapper mapper;

  MemberAdministrationController(
      MemberAdministrationService service, MemberAdministrationApiMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  /** Creates a member with a temporary password: 201 with the password, shown this once. */
  @PostMapping("/admin/organizations/{organizationId}/members")
  @ResponseStatus(HttpStatus.CREATED)
  public TemporaryPasswordResponse add(
      @PathVariable UUID organizationId,
      @AuthenticationPrincipal Jwt token,
      @Valid @RequestBody AddMemberRequest request) {
    return mapper.toResponse(
        service.add(mapper.toCommand(CurrentUser.id(token), organizationId, request)));
  }

  /** Deactivates or reactivates a member, or issues a new temporary password. */
  @PatchMapping("/admin/members/{userId}")
  public MemberChangedResponse change(
      @PathVariable UUID userId,
      @AuthenticationPrincipal Jwt token,
      @RequestBody ChangeMemberRequest request) {
    return mapper.toResponse(
        service.change(mapper.toCommand(CurrentUser.id(token), userId, request)));
  }
}
