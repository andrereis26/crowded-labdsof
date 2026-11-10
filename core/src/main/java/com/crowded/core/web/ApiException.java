package com.crowded.core.web;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * A refused request, answered as an RFC 9457 problem with a {@code code} the clients can switch on
 * (API Outline: errors such as {@code EVENT_NOT_FOUND}).
 */
public final class ApiException extends ErrorResponseException {

  @Serial private static final long serialVersionUID = 1L;

  private final String code;

  public ApiException(HttpStatus status, String code, String detail) {
    super(status, problem(status, code, detail), null);
    this.code = code;
  }

  public String getCode() {
    return code;
  }

  private static ProblemDetail problem(HttpStatus status, String code, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setProperty("code", code);
    return problem;
  }
}
