package com.crowded.core.user.domain.repository;

import com.crowded.core.user.domain.model.User;
import com.crowded.core.user.domain.model.UserAccount;
import java.util.Optional;
import java.util.UUID;

/** Users. */
public interface UserRepository {

  Optional<User> findById(UUID id);

  /** The account with this email, which must already be in lower case. */
  Optional<UserAccount> findAccountByEmail(String email);

  /**
   * Stores a new account.
   *
   * @throws com.crowded.core.shared.domain.exception.ConflictException when another account already
   *     has its email
   */
  void add(UserAccount account);
}
