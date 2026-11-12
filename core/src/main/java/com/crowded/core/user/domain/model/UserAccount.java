package com.crowded.core.user.domain.model;

import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.shared.domain.exception.NotAuthenticatedException;
import com.crowded.core.shared.domain.exception.RuleViolationException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A user's account with its credentials: the aggregate root of the user module. It changes only
 * through its methods, which keep its invariants:
 *
 * <ul>
 *   <li>a member belongs to an organization and has a name; other roles belong to none;
 *   <li>an account that must change its password has a temporary password that expires.
 * </ul>
 */
public final class UserAccount {

  /** A temporary password works for 7 days. */
  public static final Duration TEMPORARY_PASSWORD_LIFETIME = Duration.ofDays(7);

  /** The longest member name. */
  public static final int MAX_NAME_LENGTH = 120;

  private final UUID id;
  private final UUID organizationId;
  private final Role role;
  private final String name;
  private final Email email;
  private String passwordHash;
  private boolean active;
  private boolean mustChangePassword;
  private Instant temporaryPasswordExpiresAt;
  private Instant lastSignedInAt;
  private final Instant createdAt;

  private UserAccount(
      UUID id,
      UUID organizationId,
      Role role,
      String name,
      Email email,
      String passwordHash,
      boolean active,
      boolean mustChangePassword,
      Instant temporaryPasswordExpiresAt,
      Instant lastSignedInAt,
      Instant createdAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.role = Objects.requireNonNull(role, "role");
    this.email = Objects.requireNonNull(email, "email");
    this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    if ((role == Role.ORGANIZATION_MEMBER) != (organizationId != null)) {
      throw new IllegalArgumentException("A member, and only a member, has an organization");
    }
    if (role == Role.ORGANIZATION_MEMBER && name == null) {
      throw new IllegalArgumentException("A member has a name");
    }
    if (mustChangePassword && temporaryPasswordExpiresAt == null) {
      throw new IllegalArgumentException("A temporary password expires");
    }
    this.organizationId = organizationId;
    this.name = name;
    this.active = active;
    this.mustChangePassword = mustChangePassword;
    this.temporaryPasswordExpiresAt = temporaryPasswordExpiresAt;
    this.lastSignedInAt = lastSignedInAt;
  }

  /** A new participant: the only role anyone can register for. */
  public static UserAccount registerParticipant(Email email, String passwordHash, Instant now) {
    return new UserAccount(
        UUID.randomUUID(),
        null,
        Role.PARTICIPANT,
        null,
        email,
        passwordHash,
        true,
        false,
        null,
        null,
        now);
  }

  /**
   * A new member of {@code organizationId}, created by an administrator with a temporary password.
   *
   * @throws RuleViolationException when the name is blank or longer than 120 characters
   */
  public static UserAccount addMember(
      UUID organizationId, String name, Email email, String temporaryPasswordHash, Instant now) {
    Objects.requireNonNull(organizationId, "organizationId");
    return new UserAccount(
        UUID.randomUUID(),
        organizationId,
        Role.ORGANIZATION_MEMBER,
        memberName(name),
        email,
        temporaryPasswordHash,
        true,
        true,
        now.plus(TEMPORARY_PASSWORD_LIFETIME),
        null,
        now);
  }

  /** A stored account, as it was saved. */
  public static UserAccount restore(
      UUID id,
      UUID organizationId,
      Role role,
      String name,
      Email email,
      String passwordHash,
      boolean active,
      boolean mustChangePassword,
      Instant temporaryPasswordExpiresAt,
      Instant lastSignedInAt,
      Instant createdAt) {
    return new UserAccount(
        id,
        organizationId,
        role,
        name,
        email,
        passwordHash,
        active,
        mustChangePassword,
        temporaryPasswordExpiresAt,
        lastSignedInAt,
        createdAt);
  }

  /** The refusal of a sign-in, whatever was wrong, so it does not reveal which accounts exist. */
  public static NotAuthenticatedException invalidCredentials() {
    return new NotAuthenticatedException(
        "INVALID_CREDENTIALS", "The email or the password is wrong.");
  }

  /**
   * Records a sign-in at {@code now}.
   *
   * @param passwordMatches whether the password given matched this account's hash
   * @throws NotAuthenticatedException when the password is wrong or the account is deactivated
   * @throws NotAllowedException when the account still has its temporary password
   */
  public void signIn(boolean passwordMatches, Instant now) {
    if (!passwordMatches || !active) {
      throw invalidCredentials();
    }
    if (mustChangePassword) {
      throw new NotAllowedException(
          "PASSWORD_CHANGE_REQUIRED", "Set a new password before signing in.");
    }
    lastSignedInAt = now;
  }

  /**
   * Deactivates the account: it can no longer sign in.
   *
   * @return false when it already was inactive
   */
  public boolean deactivate() {
    if (!active) {
      return false;
    }
    active = false;
    return true;
  }

  /**
   * Activates the account again, with the credentials it had.
   *
   * @return false when it already was active
   */
  public boolean reactivate() {
    if (active) {
      return false;
    }
    active = true;
    return true;
  }

  /** Replaces the password with a temporary one that expires in 7 days. */
  public void resetPassword(String temporaryPasswordHash, Instant now) {
    passwordHash = Objects.requireNonNull(temporaryPasswordHash, "temporaryPasswordHash");
    mustChangePassword = true;
    temporaryPasswordExpiresAt = now.plus(TEMPORARY_PASSWORD_LIFETIME);
  }

  /** The account as a user, without its credentials. */
  public User user() {
    return User.restore(id, organizationId, role, active);
  }

  public boolean isMember() {
    return role == Role.ORGANIZATION_MEMBER;
  }

  private static String memberName(String name) {
    String stripped = name == null ? "" : name.strip();
    if (stripped.isEmpty()) {
      throw new RuleViolationException("NAME_REQUIRED", "Give the member's name.");
    }
    if (stripped.length() > MAX_NAME_LENGTH) {
      throw new RuleViolationException(
          "NAME_TOO_LONG", "The name can have at most 120 characters.");
    }
    return stripped;
  }

  public UUID getId() {
    return id;
  }

  /** The organization of a member; null for other roles. */
  public UUID getOrganizationId() {
    return organizationId;
  }

  public Role getRole() {
    return role;
  }

  /** The member's name, given by the administrator; null for participants. */
  public String getName() {
    return name;
  }

  public Email getEmail() {
    return email;
  }

  /** The password's one-way hash with salt, never the password. */
  public String getPasswordHash() {
    return passwordHash;
  }

  /** False once an administrator deactivated the account. */
  public boolean isActive() {
    return active;
  }

  /** True while the account has a temporary password. */
  public boolean isMustChangePassword() {
    return mustChangePassword;
  }

  /** When the temporary password stops working, or null. */
  public Instant getTemporaryPasswordExpiresAt() {
    return temporaryPasswordExpiresAt;
  }

  /** The last successful sign-in, or null. */
  public Instant getLastSignedInAt() {
    return lastSignedInAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof UserAccount account && id.equals(account.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "UserAccount[" + id + ", " + role + "]";
  }
}
