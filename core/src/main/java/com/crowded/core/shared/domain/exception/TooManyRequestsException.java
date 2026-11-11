package com.crowded.core.shared.domain.exception;

import java.io.Serial;

/** The caller made too many attempts in a short time (SR-015). */
public class TooManyRequestsException extends DomainException {

  @Serial private static final long serialVersionUID = 1L;

  public TooManyRequestsException(String code, String message) {
    super(code, message);
  }
}
