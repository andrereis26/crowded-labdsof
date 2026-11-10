package com.crowded.core.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A user account. Maps only the columns Core reads so far; #59 adds the rest. */
@Entity
@Table(name = "app_user")
public class AppUser {

  @Id private UUID id;

  @Column(name = "organization_id")
  private UUID organizationId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(columnDefinition = "role", nullable = false)
  private Role role;

  protected AppUser() {}

  public AppUser(UUID id, UUID organizationId, Role role) {
    this.id = id;
    this.organizationId = organizationId;
    this.role = role;
  }

  public UUID getId() {
    return id;
  }

  /** Whether this user is a member of the given organization (SR-001). */
  public boolean isMemberOf(UUID organization) {
    return role == Role.ORGANIZATION_MEMBER
        && organization != null
        && organization.equals(organizationId);
  }
}
