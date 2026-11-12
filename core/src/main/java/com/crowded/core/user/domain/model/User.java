package com.crowded.core.user.domain.model;

import java.util.Objects;
import java.util.UUID;

/** A user account, as far as authorization needs it: no credentials. */
public final class User {

  private final UUID id;
  private final UUID organizationId;
  private final Role role;
  private final boolean active;

  private User(UUID id, UUID organizationId, Role role, boolean active) {
    this.id = Objects.requireNonNull(id, "id");
    this.organizationId = organizationId;
    this.role = Objects.requireNonNull(role, "role");
    this.active = active;
  }

  /**
   * A stored user.
   *
   * @param organizationId the organization of a member; null for other roles
   * @param active false once an administrator deactivated the account
   */
  public static User restore(UUID id, UUID organizationId, Role role, boolean active) {
    return new User(id, organizationId, role, active);
  }

  /** Whether this user is an active member of the given organization. */
  public boolean isMemberOf(UUID organization) {
    return active
        && role == Role.ORGANIZATION_MEMBER
        && organization != null
        && organization.equals(organizationId);
  }

  /** Whether this user is a participant, who connects to events with a ticket or a code. */
  public boolean isParticipant() {
    return active && role == Role.PARTICIPANT;
  }

  /** Whether this user is an active platform administrator. */
  public boolean isAdministrator() {
    return active && role == Role.ADMINISTRATOR;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public Role getRole() {
    return role;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof User user && id.equals(user.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "User[" + id + ", " + role + "]";
  }
}
