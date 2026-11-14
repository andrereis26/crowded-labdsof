package com.crowded.core.shared.api.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * Logs, as an error with the request's trace id, an exception no other resolver handled. It runs
 * last and resolves nothing, so the answer stays Spring Boot's 500; Tomcat's own log of it comes
 * after the trace has ended, without its id.
 */
@Component
class UnhandledExceptionLogger implements HandlerExceptionResolver, Ordered {

  private static final Logger log = LoggerFactory.getLogger(UnhandledExceptionLogger.class);

  @Override
  public ModelAndView resolveException(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception e) {
    // The route, such as /events/{eventId}: the path itself may carry ids or codes.
    Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    log.error("Unhandled error on {} {}", request.getMethod(), route, e);
    return null;
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
