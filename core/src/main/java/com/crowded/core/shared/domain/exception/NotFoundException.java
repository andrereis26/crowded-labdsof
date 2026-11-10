package com.crowded.core.shared.domain.exception;

import java.io.Serial;

/** What the request names does not exist. */
public class NotFoundException extends DomainException {

  @Serial private static final long serialVersionUID = 1L;

  public NotFoundException(String code, String message) {
    super(code, message);
  }
}
