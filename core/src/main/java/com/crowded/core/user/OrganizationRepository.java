package com.crowded.core.user;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Organizations. */
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {}
