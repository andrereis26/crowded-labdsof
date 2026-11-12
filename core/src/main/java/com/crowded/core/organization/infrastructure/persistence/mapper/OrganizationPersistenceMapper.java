package com.crowded.core.organization.infrastructure.persistence.mapper;

import com.crowded.core.organization.domain.model.Organization;
import com.crowded.core.organization.infrastructure.persistence.entity.OrganizationEntity;
import org.mapstruct.Mapper;

/** Maps {@code organization} rows to organizations and back. */
@Mapper
public interface OrganizationPersistenceMapper {

  default Organization toDomain(OrganizationEntity entity) {
    return Organization.restore(
        entity.getId(), entity.getName(), entity.getCreatedByUserId(), entity.getCreatedAt());
  }

  OrganizationEntity toEntity(Organization organization);
}
