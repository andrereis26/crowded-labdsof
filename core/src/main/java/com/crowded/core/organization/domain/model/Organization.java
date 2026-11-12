package com.crowded.core.organization.domain.model;

import com.crowded.core.shared.domain.exception.RuleViolationException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** An organization that publishes events, created by a platform administrator. */
public final class Organization {

  /** The longest organization name. */
  public static final int MAX_NAME_LENGTH = 120;

  private final UUID id;
  private final String name;
  private final UUID createdByUserId;
  private final Instant createdAt;

  private Organization(UUID id, String name, UUID createdByUserId, Instant createdAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.name = Objects.requireNonNull(name, "name");
    this.createdByUserId = createdByUserId;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
  }

  /**
   * A new organization named {@code name}, created by {@code administratorId} at {@code now}.
   *
   * @throws RuleViolationException when the name is blank or longer than 120 characters
   */
  public static Organization create(String name, UUID administratorId, Instant now) {
    String stripped = name == null ? "" : name.strip();
    if (stripped.isEmpty()) {
      throw new RuleViolationException("NAME_REQUIRED", "Give the organization's name.");
    }
    if (stripped.length() > MAX_NAME_LENGTH) {
      throw new RuleViolationException(
          "NAME_TOO_LONG", "The name can have at most 120 characters.");
    }
    return new Organization(
        UUID.randomUUID(), stripped, Objects.requireNonNull(administratorId), now);
  }

  /** A stored organization, as it was saved. */
  public static Organization restore(
      UUID id, String name, UUID createdByUserId, Instant createdAt) {
    return new Organization(id, name, createdByUserId, createdAt);
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  /** The administrator who created it; null once their account is deleted. */
  public UUID getCreatedByUserId() {
    return createdByUserId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Organization organization && id.equals(organization.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "Organization[" + id + ", " + name + "]";
  }
}
