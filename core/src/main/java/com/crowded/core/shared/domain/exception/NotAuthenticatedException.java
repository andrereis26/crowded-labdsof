package com.crowded.core.shared.domain.exception;

import java.io.Serial;

/** The caller could not be identified, such as a sign-in with wrong credentials. */
public class NotAuthenticatedException extends DomainException {

  @Serial private static final long serialVersionUID = 1L;

  public NotAuthenticatedException(String code, String message) {
    super(code, message);
  }
}
