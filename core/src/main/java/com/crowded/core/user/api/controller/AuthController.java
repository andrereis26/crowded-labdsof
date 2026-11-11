package com.crowded.core.user.api.controller;

import com.crowded.core.user.api.dto.RegisterRequest;
import com.crowded.core.user.api.dto.RegisteredResponse;
import com.crowded.core.user.api.dto.SignInRequest;
import com.crowded.core.user.api.dto.SignInResponse;
import com.crowded.core.user.application.dto.RegisterCommand;
import com.crowded.core.user.application.dto.SignInCommand;
import com.crowded.core.user.application.dto.SignedIn;
import com.crowded.core.user.application.service.AuthService;
import com.crowded.core.user.domain.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration and sign-in (API Outline, Identity). Public: the rate limits use the client's
 * address, which the router sends in {@code X-Forwarded-For} (server.forward-headers-strategy).
 */
@RestController
public class AuthController {

  private final AuthService service;

  public AuthController(AuthService service) {
    this.service = service;
  }

  /** Creates a participant account: 201 with its id and role. */
  @PostMapping("/auth/register")
  @ResponseStatus(HttpStatus.CREATED)
  public RegisteredResponse register(
      @Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
    User user =
        service.register(
            new RegisterCommand(request.email(), request.password(), http.getRemoteAddr()));
    return new RegisteredResponse(user.id(), user.role().name());
  }

  /** Signs in: a short-lived token and who it is for. */
  @PostMapping("/auth/login")
  public SignInResponse signIn(@Valid @RequestBody SignInRequest request, HttpServletRequest http) {
    SignedIn signedIn =
        service.signIn(
            new SignInCommand(request.email(), request.password(), http.getRemoteAddr()));
    User user = signedIn.user();
    return new SignInResponse(
        signedIn.token().value(),
        signedIn.token().expiresAt(),
        signedIn.mustChangePassword(),
        new SignInResponse.SignedInUser(user.id(), user.role().name(), user.organizationId()));
  }
}
