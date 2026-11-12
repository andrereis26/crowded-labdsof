package com.crowded.core.user.domain.model;

import java.util.UUID;

/**
 * A user account, as far as authorization needs it.
 *
 * @param organizationId the organization of a member; null for other roles
 * @param active false once an administrator deactivated the account (FR-087)
 */
public record User(UUID id, UUID organizationId, Role role, boolean active) {

  /** Whether this user is an active member of the given organization (SR-001). */
  public boolean isMemberOf(UUID organization) {
    return active
        && role == Role.ORGANIZATION_MEMBER
        && organization != null
        && organization.equals(organizationId);
  }

  /** Whether this user is an active platform administrator (FR-087). */
  public boolean isAdministrator() {
    return active && role == Role.ADMINISTRATOR;
  }
}
