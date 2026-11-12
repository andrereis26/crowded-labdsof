package com.crowded.notifications.shared.domain.exception;

import java.io.Serial;

/**
 * Something the domain refuses to do, with a {@code code} that the logs and, later, the API can
 * switch on, as in Core.
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
