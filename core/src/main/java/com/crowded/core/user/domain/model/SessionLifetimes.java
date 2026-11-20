package com.crowded.core.user.domain.model;

import java.util.Objects;

/**
 * The session lifetime of each role (ADR 0021). Organization members and administrators change
 * events and accounts, so their sessions are shorter than a participant's.
 */
public record SessionLifetimes(SessionLifetime participant, SessionLifetime backoffice) {

  public SessionLifetimes {
    Objects.requireNonNull(participant, "participant");
    Objects.requireNonNull(backoffice, "backoffice");
  }

  /** The lifetime of a session started by a user with this role. */
  public SessionLifetime forRole(Role role) {
    return role == Role.PARTICIPANT ? participant : backoffice;
  }
}
