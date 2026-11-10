package com.crowded.core.shared.domain.exception;

import java.io.Serial;

/**
 * A request the domain refuses, with a {@code code} the clients can switch on (API Outline: errors
 * such as {@code EVENT_NOT_FOUND}). Its subclasses say what kind of refusal it is; the API layer
 * turns that into the HTTP status.
 */
public abstract class DomainException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final String code;

  protected DomainException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
