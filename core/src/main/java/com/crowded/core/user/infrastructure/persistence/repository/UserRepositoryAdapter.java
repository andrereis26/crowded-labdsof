package com.crowded.core.user.infrastructure.persistence.repository;

import com.crowded.core.shared.domain.exception.ConflictException;
import com.crowded.core.user.domain.model.User;
import com.crowded.core.user.domain.model.UserAccount;
import com.crowded.core.user.domain.repository.UserRepository;
import com.crowded.core.user.infrastructure.persistence.mapper.UserPersistenceMapper;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
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

  @Override
  public Optional<UserAccount> findAccountByEmail(String email) {
    return users.findByEmail(email).map(mapper::toAccount);
  }

  @Override
  public void add(UserAccount account) {
    try {
      // Flushed now, so a second account with the same email fails here (app_user_email_key).
      users.saveAndFlush(mapper.toEntity(account));
    } catch (DataIntegrityViolationException e) {
      throw emailTaken();
    }
  }

  static ConflictException emailTaken() {
    return new ConflictException(
        "EMAIL_ALREADY_REGISTERED", "There is already an account with this email.");
  }
}
