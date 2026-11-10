package com.crowded.core.shared.domain.exception;

import java.io.Serial;

/** The user may not do this. */
public class NotAllowedException extends DomainException {

  @Serial private static final long serialVersionUID = 1L;

  public NotAllowedException(String code, String message) {
    super(code, message);
  }
}
