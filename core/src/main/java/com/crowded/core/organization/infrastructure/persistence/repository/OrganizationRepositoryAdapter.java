package com.crowded.core.organization.infrastructure.persistence.repository;

import com.crowded.core.organization.domain.model.Organization;
import com.crowded.core.organization.domain.repository.OrganizationRepository;
import com.crowded.core.organization.infrastructure.persistence.mapper.OrganizationPersistenceMapper;
import com.crowded.core.shared.domain.exception.ConflictException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
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

  @Override
  public List<Organization> findAll() {
    return organizations.findAllByOrderByNameAsc().stream().map(mapper::toDomain).toList();
  }

  @Override
  public void add(Organization organization) {
    try {
      // Flushed now, so a second organization with the same name fails here
      // (organization_name_key).
      organizations.saveAndFlush(mapper.toEntity(organization));
    } catch (DataIntegrityViolationException e) {
      throw new ConflictException(
          "ORGANIZATION_NAME_TAKEN", "There is already an organization with this name.");
    }
  }
}
