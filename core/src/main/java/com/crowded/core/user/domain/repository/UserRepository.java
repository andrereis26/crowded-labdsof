package com.crowded.core.user.domain.repository;

import com.crowded.core.user.domain.model.User;
import java.util.Optional;
import java.util.UUID;

/** Users. */
public interface UserRepository {

  Optional<User> findById(UUID id);
}
