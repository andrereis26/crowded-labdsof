package com.crowded.core.user.api.controller;

import com.crowded.core.user.api.dto.RegisterRequest;
import com.crowded.core.user.api.dto.RegisteredResponse;
import com.crowded.core.user.api.dto.SignInRequest;
import com.crowded.core.user.api.dto.SignInResponse;
import com.crowded.core.user.api.mapper.AuthApiMapper;
import com.crowded.core.user.application.service.AuthService;
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
  private final AuthApiMapper mapper;

  AuthController(AuthService service, AuthApiMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  /** Creates a participant account: 201 with its id and role. */
  @PostMapping("/auth/register")
  @ResponseStatus(HttpStatus.CREATED)
  public RegisteredResponse register(
      @Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
    return mapper.toRegisteredResponse(
        service.register(mapper.toCommand(request, http.getRemoteAddr())));
  }

  /** Signs in: a short-lived token and who it is for. */
  @PostMapping("/auth/login")
  public SignInResponse signIn(@Valid @RequestBody SignInRequest request, HttpServletRequest http) {
    return mapper.toSignInResponse(service.signIn(mapper.toCommand(request, http.getRemoteAddr())));
  }
}
