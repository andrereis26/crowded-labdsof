package com.crowded.core.organization.domain.repository;

import com.crowded.core.organization.domain.model.Organization;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Organizations. */
public interface OrganizationRepository {

  Optional<Organization> findById(UUID id);

  /** Every organization, by name. */
  List<Organization> findAll();

  /**
   * Stores a new organization.
   *
   * @throws com.crowded.core.shared.domain.exception.ConflictException when another organization
   *     has its name, whatever its case
   */
  void add(Organization organization);
}
