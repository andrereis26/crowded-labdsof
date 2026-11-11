package com.crowded.core.shared.domain.exception;

import java.io.Serial;

/** A dependency the request needs is down, so it is refused instead of being let through. */
public class UnavailableException extends DomainException {

  @Serial private static final long serialVersionUID = 1L;

  public UnavailableException(String code, String message) {
    super(code, message);
  }
}
