package com.crowded.notifications.shared.api.controller;

import com.crowded.notifications.shared.domain.exception.NotAuthenticatedException;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** The signed-in user of a request: the token's subject, as in Core. */
public final class CurrentUser {

  private CurrentUser() {}

  /**
   * @throws NotAuthenticatedException when the token names no user
   */
  public static UUID id(Jwt token) {
    try {
      String subject = token == null ? null : token.getSubject();
      if (subject != null) {
        return UUID.fromString(subject);
      }
    } catch (IllegalArgumentException e) {
      // Not a user id: refused below.
    }
    throw new NotAuthenticatedException("INVALID_TOKEN", "The token names no user.");
  }
}
