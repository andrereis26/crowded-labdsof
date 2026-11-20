package com.crowded.core.shared.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.stereotype.Component;

/**
 * Finds the access token in the {@code Authorization} header, as the phone app sends it, or else in
 * the browser's session cookie (ADR 0021). The {@code /auth} endpoints read no token here: they
 * start, refresh or end a session, so an expired token must not refuse them.
 */
@Component
public class SessionBearerTokenResolver implements BearerTokenResolver {

  private final BearerTokenResolver header = new DefaultBearerTokenResolver();

  @Override
  public String resolve(HttpServletRequest request) {
    if (request.getRequestURI().startsWith("/auth/")) {
      return null;
    }
    String token = header.resolve(request);
    return token != null ? token : SessionCookies.accessToken(request).orElse(null);
  }
}
