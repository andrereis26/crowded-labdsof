package com.crowded.core.user.infrastructure.persistence.entity;

import com.crowded.core.user.domain.model.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Row of {@code app_user}. Maps the columns Core reads and writes so far; the others keep their
 * database default when a row is inserted.
 */
@Entity
@Table(name = "app_user")
public class UserEntity {

  @Id private UUID id;

  @Column(name = "organization_id")
  private UUID organizationId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(columnDefinition = "role", nullable = false)
  private Role role;

  private String name;

  @Column(nullable = false)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "must_change_password", nullable = false)
  private boolean mustChangePassword;

  @Column(name = "temporary_password_expires_at")
  private Instant temporaryPasswordExpiresAt;

  @Column(name = "last_signed_in_at")
  private Instant lastSignedInAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(UUID organizationId) {
    this.organizationId = organizationId;
  }

  public Role getRole() {
    return role;
  }

  public void setRole(Role role) {
    this.role = role;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public boolean isMustChangePassword() {
    return mustChangePassword;
  }

  public void setMustChangePassword(boolean mustChangePassword) {
    this.mustChangePassword = mustChangePassword;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Instant getTemporaryPasswordExpiresAt() {
    return temporaryPasswordExpiresAt;
  }

  public void setTemporaryPasswordExpiresAt(Instant temporaryPasswordExpiresAt) {
    this.temporaryPasswordExpiresAt = temporaryPasswordExpiresAt;
  }

  public Instant getLastSignedInAt() {
    return lastSignedInAt;
  }

  public void setLastSignedInAt(Instant lastSignedInAt) {
    this.lastSignedInAt = lastSignedInAt;
  }
}
