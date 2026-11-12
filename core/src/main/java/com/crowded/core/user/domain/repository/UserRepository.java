package com.crowded.core.user.domain.repository;

import com.crowded.core.user.domain.model.Email;
import com.crowded.core.user.domain.model.User;
import com.crowded.core.user.domain.model.UserAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Users. */
public interface UserRepository {

  Optional<User> findById(UUID id);

  Optional<UserAccount> findAccountById(UUID id);

  /** The account with this email. */
  Optional<UserAccount> findAccountByEmail(Email email);

  /** Every organization member's account, active or not. */
  List<UserAccount> findMembers();

  /**
   * Stores a new account.
   *
   * @throws com.crowded.core.shared.domain.exception.ConflictException when another account already
   *     has its email
   */
  void add(UserAccount account);

  /** Stores the new state of an account that exists. */
  void update(UserAccount account);

  /**
   * Stores the sign-in that {@code account} recorded ({@link UserAccount#signIn}), and only that,
   * so it does not undo a change an administrator made meanwhile.
   */
  void saveSignIn(UserAccount account);
}
