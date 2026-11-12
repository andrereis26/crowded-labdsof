package com.crowded.core.user.domain.repository;

/** One-way password hashing with salt. */
public interface PasswordHasher {

  /** The hash to store for {@code password}. */
  String hash(String password);

  /** Whether {@code password} is the one {@code hash} was made from. */
  boolean matches(String password, String hash);
}
