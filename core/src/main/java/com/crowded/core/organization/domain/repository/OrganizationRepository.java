package com.crowded.core.organization.domain.repository;

import com.crowded.core.organization.domain.model.Organization;
import java.util.Optional;
import java.util.UUID;

/** Organizations. */
public interface OrganizationRepository {

  Optional<Organization> findById(UUID id);
}
