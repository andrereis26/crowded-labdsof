package com.crowded.notifications.shared.api.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.stereotype.Component;

/**
 * Finds the access token in the {@code Authorization} header, as the phone app sends it, or else in
 * the browser's session cookie, which Core sets (ADR 0021).
 */
@Component
public class SessionBearerTokenResolver implements BearerTokenResolver {

  /** Core's cookie with the access token. */
  public static final String ACCESS_COOKIE = "crowded_access";

  private final BearerTokenResolver header = new DefaultBearerTokenResolver();

  @Override
  public String resolve(HttpServletRequest request) {
    String token = header.resolve(request);
    return token != null ? token : cookie(request);
  }

  /** The access token cookie's value, if the browser sent one. */
  static String cookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    return Arrays.stream(cookies)
        .filter(cookie -> ACCESS_COOKIE.equals(cookie.getName()))
        .map(Cookie::getValue)
        .filter(value -> !value.isBlank())
        .findFirst()
        .orElse(null);
  }
}
