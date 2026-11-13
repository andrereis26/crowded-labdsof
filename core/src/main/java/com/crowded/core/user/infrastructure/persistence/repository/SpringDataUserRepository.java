package com.crowded.core.user.infrastructure.persistence.repository;

import com.crowded.core.user.domain.model.Role;
import com.crowded.core.user.infrastructure.persistence.entity.UserEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Spring Data access to {@code app_user}. */
interface SpringDataUserRepository extends JpaRepository<UserEntity, UUID> {

  Optional<UserEntity> findByEmail(String email);

  List<UserEntity> findByRoleOrderByNameAsc(Role role);

  @Modifying
  @Query("update UserEntity u set u.lastSignedInAt = :at where u.id = :id")
  void updateLastSignedInAt(UUID id, Instant at);

  @Modifying
  @Query(
      "update UserEntity u set u.passwordHash = :passwordHash, u.mustChangePassword = false,"
          + " u.temporaryPasswordExpiresAt = null, u.lastSignedInAt = :at where u.id = :id")
  void updateChosenPassword(UUID id, String passwordHash, Instant at);
}
