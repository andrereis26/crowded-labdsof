package com.crowded.core.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** An organization. Maps only the columns Core reads so far; #140 adds the rest. */
@Entity
@Table(name = "organization")
public class Organization {

  @Id private UUID id;

  @Column(nullable = false)
  private String name;

  protected Organization() {}

  public Organization(UUID id, String name) {
    this.id = id;
    this.name = name;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }
}
