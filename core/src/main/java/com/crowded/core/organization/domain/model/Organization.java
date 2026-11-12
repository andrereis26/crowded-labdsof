package com.crowded.core.organization.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * An organization that publishes events, created by a platform administrator (FR-087).
 *
 * @param createdByUserId the administrator who created it; null once their account is deleted
 */
public record Organization(UUID id, String name, UUID createdByUserId, Instant createdAt) {

  public static Organization create(UUID id, String name, UUID administratorId, Instant now) {
    return new Organization(id, name, administratorId, now);
  }
}
