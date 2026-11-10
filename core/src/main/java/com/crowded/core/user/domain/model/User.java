package com.crowded.core.user.domain.model;

import java.util.UUID;

/**
 * A user account, as far as Core needs it so far; #59 adds the rest.
 *
 * @param organizationId the organization of a member; null for other roles
 */
public record User(UUID id, UUID organizationId, Role role) {

  /** Whether this user is a member of the given organization (SR-001). */
  public boolean isMemberOf(UUID organization) {
    return role == Role.ORGANIZATION_MEMBER
        && organization != null
        && organization.equals(organizationId);
  }
}
