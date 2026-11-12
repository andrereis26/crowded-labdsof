package com.crowded.notifications.shared.api.exception;

import com.crowded.notifications.shared.domain.exception.DomainException;
import com.crowded.notifications.shared.domain.exception.NotAuthenticatedException;
import com.crowded.notifications.shared.domain.exception.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Answers a domain refusal as an RFC 9457 problem, with the domain's error code in "code". */
@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(DomainException.class)
  public ProblemDetail handle(DomainException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(status(exception), exception.getMessage());
    problem.setProperty("code", exception.getCode());
    return problem;
  }

  private static HttpStatus status(DomainException exception) {
    return switch (exception) {
      case NotFoundException ignored -> HttpStatus.NOT_FOUND;
      case NotAuthenticatedException ignored -> HttpStatus.UNAUTHORIZED;
      default -> HttpStatus.BAD_REQUEST;
    };
  }
}
