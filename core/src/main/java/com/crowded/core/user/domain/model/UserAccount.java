package com.crowded.core.user.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A user's account with its credentials, for registration and sign-in (FR-039, FR-043).
 *
 * @param organizationId the organization of a member; null for other roles
 * @param email the sign-in email, in lower case
 * @param passwordHash the password's one-way hash with salt (SR-013), never the password
 * @param active false once an administrator deactivated the account (FR-087)
 * @param mustChangePassword true while the account has a temporary password (FR-088)
 */
public record UserAccount(
    UUID id,
    UUID organizationId,
    Role role,
    String email,
    String passwordHash,
    boolean active,
    boolean mustChangePassword,
    Instant createdAt) {

  /** A new participant: the only role anyone can register for (FR-043). */
  public static UserAccount newParticipant(
      UUID id, String email, String passwordHash, Instant now) {
    return new UserAccount(id, null, Role.PARTICIPANT, email, passwordHash, true, false, now);
  }

  /** The account as a user, without its credentials. */
  public User user() {
    return new User(id, organizationId, role, active);
  }
}
