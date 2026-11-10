package com.crowded.core.user.infrastructure.persistence.repository;

import com.crowded.core.user.domain.model.User;
import com.crowded.core.user.domain.repository.UserRepository;
import com.crowded.core.user.infrastructure.persistence.mapper.UserPersistenceMapper;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** {@link UserRepository} on PostgreSQL. */
@Repository
class UserRepositoryAdapter implements UserRepository {

  private final SpringDataUserRepository users;
  private final UserPersistenceMapper mapper;

  UserRepositoryAdapter(SpringDataUserRepository users, UserPersistenceMapper mapper) {
    this.users = users;
    this.mapper = mapper;
  }

  @Override
  public Optional<User> findById(UUID id) {
    return users.findById(id).map(mapper::toDomain);
  }
}
