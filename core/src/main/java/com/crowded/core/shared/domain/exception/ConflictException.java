package com.crowded.core.shared.domain.exception;

import java.io.Serial;

/** The request conflicts with the current state, such as a change to a closed event. */
public class ConflictException extends DomainException {

  @Serial private static final long serialVersionUID = 1L;

  public ConflictException(String code, String message) {
    super(code, message);
  }
}
