package com.crowded.core.user;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Users. */
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {}
