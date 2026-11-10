package com.crowded.core.user.infrastructure.persistence.repository;

import com.crowded.core.user.infrastructure.persistence.entity.UserEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@code app_user}. */
interface SpringDataUserRepository extends JpaRepository<UserEntity, UUID> {}
