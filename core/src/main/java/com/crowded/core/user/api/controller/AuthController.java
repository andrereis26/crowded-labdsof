package com.crowded.core.user.api.controller;

import com.crowded.core.shared.api.controller.SessionCookies;
import com.crowded.core.user.api.dto.ChoosePasswordRequest;
import com.crowded.core.user.api.dto.RegisterRequest;
import com.crowded.core.user.api.dto.RegisteredResponse;
import com.crowded.core.user.api.dto.SessionTokenRequest;
import com.crowded.core.user.api.dto.SignInRequest;
import com.crowded.core.user.api.dto.SignInResponse;
import com.crowded.core.user.api.dto.TokenDelivery;
import com.crowded.core.user.api.mapper.AuthApiMapper;
import com.crowded.core.user.application.dto.SignOutCommand;
import com.crowded.core.user.application.dto.SignedIn;
import com.crowded.core.user.application.service.AuthService;
import com.crowded.core.user.application.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration, sign-in, the first password a member chooses, and the session that follows (API
 * Outline, Accounts; ADR 0021). Public: the rate limits use the client's address, which the router
 * sends in {@code X-Forwarded-For} (server.forward-headers-strategy).
 */
@RestController
public class AuthController {

  private final AuthService service;
  private final SessionService sessions;
  private final AuthApiMapper mapper;
  private final SessionCookies cookies;
  private final JwtDecoder decoder;
  private final DefaultBearerTokenResolver header = new DefaultBearerTokenResolver();

  AuthController(
      AuthService service,
      SessionService sessions,
      AuthApiMapper mapper,
      SessionCookies cookies,
      JwtDecoder decoder) {
    this.service = service;
    this.sessions = sessions;
    this.mapper = mapper;
    this.cookies = cookies;
    this.decoder = decoder;
  }

  /** Creates a participant account: 201 with its id and role. */
  @PostMapping("/auth/register")
  @ResponseStatus(HttpStatus.CREATED)
  public RegisteredResponse register(
      @Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
    return mapper.toRegisteredResponse(
        service.register(mapper.toCommand(request, http.getRemoteAddr())));
  }

  /** Signs in and starts a session. */
  @PostMapping("/auth/login")
  public SignInResponse signIn(
      @Valid @RequestBody SignInRequest request,
      HttpServletRequest http,
      HttpServletResponse response) {
    SignedIn signedIn = service.signIn(mapper.toCommand(request, http.getRemoteAddr()));
    return respond(signedIn, request.delivery() == TokenDelivery.COOKIE, response);
  }

  /**
   * Replaces a member's temporary password with the one they chose, at their first sign-in, and
   * starts a session. Public: the temporary password proves who it is, and it gives no token.
   */
  @PostMapping("/auth/password")
  public SignInResponse choosePassword(
      @Valid @RequestBody ChoosePasswordRequest request,
      HttpServletRequest http,
      HttpServletResponse response) {
    SignedIn signedIn =
        service.replaceTemporaryPassword(mapper.toCommand(request, http.getRemoteAddr()));
    return respond(signedIn, request.delivery() == TokenDelivery.COOKIE, response);
  }

  /**
   * Continues a session with new tokens, answered the way the refresh token came: in cookies for a
   * browser, in the body for the phone app.
   */
  @PostMapping("/auth/refresh")
  public SignInResponse refresh(
      @RequestBody(required = false) SessionTokenRequest request,
      HttpServletRequest http,
      HttpServletResponse response) {
    Optional<String> cookie = SessionCookies.refreshToken(http);
    String token = cookie.orElse(request == null ? null : request.refreshToken());
    return respond(sessions.refresh(token), cookie.isPresent(), response);
  }

  /**
   * Ends the session: its refresh token, its access token while it is valid, and the browser's
   * cookies. 204 even when there was no session, so signing out always succeeds.
   */
  @PostMapping("/auth/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void signOut(
      @RequestBody(required = false) SessionTokenRequest request,
      HttpServletRequest http,
      HttpServletResponse response) {
    String refreshToken =
        SessionCookies.refreshToken(http).orElse(request == null ? null : request.refreshToken());
    // First, so the browser is signed out even when Redis cannot be reached (503).
    cookies.clear(response);
    Optional<Jwt> access = validAccessToken(http);
    sessions.signOut(
        new SignOutCommand(
            refreshToken,
            access.map(Jwt::getId).orElse(null),
            access.map(Jwt::getExpiresAt).orElse(null)));
  }

  private SignInResponse respond(
      SignedIn signedIn, boolean inCookies, HttpServletResponse response) {
    if (!inCookies) {
      return mapper.toSignInResponse(signedIn);
    }
    cookies.write(
        response,
        signedIn.token().value(),
        signedIn.token().expiresAt(),
        signedIn.refreshToken(),
        signedIn.refreshExpiresAt());
    return mapper.toCookieSignInResponse(signedIn);
  }

  /** The request's access token, from the header or the cookie, while it is still valid. */
  private Optional<Jwt> validAccessToken(HttpServletRequest http) {
    String token;
    try {
      token = header.resolve(http);
    } catch (RuntimeException e) {
      token = null;
    }
    String value = token != null ? token : SessionCookies.accessToken(http).orElse(null);
    if (value == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(decoder.decode(value));
    } catch (JwtException e) {
      // Expired or already revoked: nothing left to revoke.
      return Optional.empty();
    }
  }
}
