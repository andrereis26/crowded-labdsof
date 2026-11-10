package com.crowded.core.shared.domain.exception;

import java.io.Serial;

/** The request breaks a business rule, such as an invalid value. */
public class RuleViolationException extends DomainException {

  @Serial private static final long serialVersionUID = 1L;

  public RuleViolationException(String code, String message) {
    super(code, message);
  }
}
