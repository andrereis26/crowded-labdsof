package com.crowded.core.user.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * A user's account with its credentials (FR-039, FR-043, FR-087).
 *
 * @param organizationId the organization of a member; null for other roles
 * @param name the member's name, given by the administrator; null for participants
 * @param email the sign-in email, in lower case
 * @param passwordHash the password's one-way hash with salt (SR-013), never the password
 * @param active false once an administrator deactivated the account (FR-087)
 * @param mustChangePassword true while the account has a temporary password (FR-088)
 * @param temporaryPasswordExpiresAt when the temporary password stops working (SR-022)
 * @param lastSignedInAt the last successful sign-in, or null
 */
public record UserAccount(
    UUID id,
    UUID organizationId,
    Role role,
    String name,
    String email,
    String passwordHash,
    boolean active,
    boolean mustChangePassword,
    Instant temporaryPasswordExpiresAt,
    Instant lastSignedInAt,
    Instant createdAt) {

  /** A temporary password works for 7 days (SR-022). */
  public static final Duration TEMPORARY_PASSWORD_LIFETIME = Duration.ofDays(7);

  /** A new participant: the only role anyone can register for (FR-043). */
  public static UserAccount newParticipant(
      UUID id, String email, String passwordHash, Instant now) {
    return new UserAccount(
        id, null, Role.PARTICIPANT, null, email, passwordHash, true, false, null, null, now);
  }

  /**
   * A new member of {@code organizationId}, created by an administrator with a temporary password
   * (FR-087, SR-022).
   */
  public static UserAccount newMember(
      UUID id,
      UUID organizationId,
      String name,
      String email,
      String temporaryPasswordHash,
      Instant now) {
    return new UserAccount(
        id,
        organizationId,
        Role.ORGANIZATION_MEMBER,
        name,
        email,
        temporaryPasswordHash,
        true,
        true,
        now.plus(TEMPORARY_PASSWORD_LIFETIME),
        null,
        now);
  }

  /** The account as a user, without its credentials. */
  public User user() {
    return new User(id, organizationId, role, active);
  }

  public boolean isMember() {
    return role == Role.ORGANIZATION_MEMBER;
  }

  /** The account, deactivated: it can no longer sign in (FR-087, SR-001). */
  public UserAccount deactivated() {
    return withActive(false);
  }

  /** The account, active again, with the credentials it had. */
  public UserAccount reactivated() {
    return withActive(true);
  }

  private UserAccount withActive(boolean nowActive) {
    return new UserAccount(
        id,
        organizationId,
        role,
        name,
        email,
        passwordHash,
        nowActive,
        mustChangePassword,
        temporaryPasswordExpiresAt,
        lastSignedInAt,
        createdAt);
  }

  /** The account with a new temporary password, which replaces the one it had (FR-087). */
  public UserAccount resetPassword(String temporaryPasswordHash, Instant now) {
    return new UserAccount(
        id,
        organizationId,
        role,
        name,
        email,
        temporaryPasswordHash,
        active,
        true,
        now.plus(TEMPORARY_PASSWORD_LIFETIME),
        lastSignedInAt,
        createdAt);
  }
}
