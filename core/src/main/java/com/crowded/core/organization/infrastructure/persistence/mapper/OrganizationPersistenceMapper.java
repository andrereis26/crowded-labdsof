package com.crowded.core.organization.infrastructure.persistence.mapper;

import com.crowded.core.organization.domain.model.Organization;
import com.crowded.core.organization.infrastructure.persistence.entity.OrganizationEntity;
import org.mapstruct.Mapper;

/** Maps {@code organization} rows to organizations and back. */
@Mapper
public interface OrganizationPersistenceMapper {

  Organization toDomain(OrganizationEntity entity);

  OrganizationEntity toEntity(Organization organization);
}
