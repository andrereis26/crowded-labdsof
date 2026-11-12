package com.crowded.notifications.shared.domain.exception;

import java.io.Serial;

/** What the request names does not exist, or is not the caller's to see. */
public class NotFoundException extends DomainException {

  @Serial private static final long serialVersionUID = 1L;

  public NotFoundException(String code, String message) {
    super(code, message);
  }
}
