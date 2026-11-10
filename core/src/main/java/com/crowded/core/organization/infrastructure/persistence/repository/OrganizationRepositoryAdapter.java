package com.crowded.core.organization.infrastructure.persistence.repository;

import com.crowded.core.organization.domain.model.Organization;
import com.crowded.core.organization.domain.repository.OrganizationRepository;
import com.crowded.core.organization.infrastructure.persistence.mapper.OrganizationPersistenceMapper;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** {@link OrganizationRepository} on PostgreSQL. */
@Repository
class OrganizationRepositoryAdapter implements OrganizationRepository {

  private final SpringDataOrganizationRepository organizations;
  private final OrganizationPersistenceMapper mapper;

  OrganizationRepositoryAdapter(
      SpringDataOrganizationRepository organizations, OrganizationPersistenceMapper mapper) {
    this.organizations = organizations;
    this.mapper = mapper;
  }

  @Override
  public Optional<Organization> findById(UUID id) {
    return organizations.findById(id).map(mapper::toDomain);
  }
}
