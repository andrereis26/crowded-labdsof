package com.crowded.notifications.shared.api.controller;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Refuses a change sent from another site with the browser's session (ADR 0021). A POST, PUT, PATCH
 * or DELETE must come from the host it was sent to when it carries an {@code Origin} header, which
 * browsers always send, or a session cookie. The phone app sends neither, and a browser cannot add
 * the {@code Authorization} header to another site's request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class OriginCheckFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(OriginCheckFilter.class);
  private static final Set<String> CHANGES = Set.of("POST", "PUT", "PATCH", "DELETE");

  private final JsonMapper json;

  OriginCheckFilter(JsonMapper json) {
    this.json = json;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !CHANGES.contains(request.getMethod());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String origin = request.getHeader(HttpHeaders.ORIGIN);
    boolean allowed =
        origin == null
            ? SessionBearerTokenResolver.cookie(request) == null
            : sameHost(origin, host(request));
    if (allowed) {
      chain.doFilter(request, response);
      return;
    }
    log.warn("Refused a {} from another origin", request.getMethod());
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN, "Changes are accepted only from Crowded's own pages.");
    problem.setProperty("code", "ORIGIN_NOT_ALLOWED");
    response.setStatus(HttpStatus.FORBIDDEN.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.getWriter().write(json.writeValueAsString(problem));
  }

  /** The host and port the request was sent to, as the browser wrote it. */
  private static String host(HttpServletRequest request) {
    String host = request.getHeader(HttpHeaders.HOST);
    if (host != null) {
      return host;
    }
    int port = request.getServerPort();
    return request.getServerName() + (port == 80 || port == 443 ? "" : ":" + port);
  }

  /** Whether {@code origin} names {@code host}, the host and port the browser sent it to. */
  static boolean sameHost(String origin, String host) {
    if (host == null) {
      return false;
    }
    try {
      URI uri = new URI(origin);
      if (uri.getHost() == null) {
        return false;
      }
      String authority = uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
      return authority.toLowerCase(Locale.ROOT).equals(host.toLowerCase(Locale.ROOT));
    } catch (URISyntaxException e) {
      return false;
    }
  }
}
