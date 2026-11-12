package com.crowded.core.organization.infrastructure.persistence.repository;

import com.crowded.core.organization.infrastructure.persistence.entity.OrganizationEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@code organization}. */
interface SpringDataOrganizationRepository extends JpaRepository<OrganizationEntity, UUID> {

  List<OrganizationEntity> findAllByOrderByNameAsc();
}
