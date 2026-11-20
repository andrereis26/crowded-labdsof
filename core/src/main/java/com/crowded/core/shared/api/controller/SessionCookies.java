package com.crowded.core.shared.api.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The cookies that hold a browser's session (ADR 0021): the access token for the whole API and the
 * refresh token for {@code /auth} only. Page scripts cannot read either. Core does not see the
 * {@code /api} prefix the router removes, so the path comes from configuration.
 */
@Component
public class SessionCookies {

  public static final String ACCESS = "crowded_access";
  public static final String REFRESH = "crowded_refresh";

  private final String path;
  private final boolean secure;
  private final Clock clock;

  SessionCookies(
      @Value("${crowded.sessions.cookie-path:/api}") String path,
      @Value("${crowded.sessions.cookie-secure:true}") boolean secure,
      Clock clock) {
    this.path = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    this.secure = secure;
    this.clock = clock;
  }

  /** Sets both cookies, each until its token expires. */
  public void write(
      HttpServletResponse response,
      String accessToken,
      Instant accessExpiresAt,
      String refreshToken,
      Instant refreshExpiresAt) {
    add(response, ACCESS, accessToken, accessPath(), until(accessExpiresAt));
    add(response, REFRESH, refreshToken, refreshPath(), until(refreshExpiresAt));
  }

  /** Deletes both cookies, which page scripts cannot do themselves. */
  public void clear(HttpServletResponse response) {
    add(response, ACCESS, "", accessPath(), Duration.ZERO);
    add(response, REFRESH, "", refreshPath(), Duration.ZERO);
  }

  /** The refresh token the browser sent, if any. */
  public static Optional<String> refreshToken(HttpServletRequest request) {
    return value(request, REFRESH);
  }

  /** The access token the browser sent, if any. */
  public static Optional<String> accessToken(HttpServletRequest request) {
    return value(request, ACCESS);
  }

  /** Whether the request carries either session cookie. */
  public static boolean present(HttpServletRequest request) {
    return accessToken(request).isPresent() || refreshToken(request).isPresent();
  }

  private String accessPath() {
    return path.isEmpty() ? "/" : path;
  }

  private String refreshPath() {
    return path + "/auth";
  }

  private Duration until(Instant expiresAt) {
    Duration remaining = Duration.between(clock.instant(), expiresAt);
    return remaining.isNegative() ? Duration.ZERO : remaining;
  }

  private void add(
      HttpServletResponse response, String name, String value, String cookiePath, Duration age) {
    ResponseCookie cookie =
        ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Strict")
            .path(cookiePath)
            .maxAge(age)
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  private static Optional<String> value(HttpServletRequest request, String name) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return Optional.empty();
    }
    return Arrays.stream(cookies)
        .filter(cookie -> name.equals(cookie.getName()))
        .map(Cookie::getValue)
        .filter(value -> !value.isBlank())
        .findFirst();
  }
}
