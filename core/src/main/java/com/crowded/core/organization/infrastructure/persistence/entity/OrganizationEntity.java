package com.crowded.core.organization.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Row of {@code organization}. Maps only the columns Core reads so far. */
@Entity
@Table(name = "organization")
public class OrganizationEntity {

  @Id private UUID id;

  @Column(nullable = false)
  private String name;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
