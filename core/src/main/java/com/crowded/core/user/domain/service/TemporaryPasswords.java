package com.crowded.core.user.domain.service;

import java.util.random.RandomGenerator;

/**
 * Temporary passwords an administrator gives a member (FR-087), such as {@code T7q-mK4-v9Px}: ten
 * random characters without the ones easily confused when read aloud or copied (0, O, 1, l, I).
 */
public final class TemporaryPasswords {

  static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

  private TemporaryPasswords() {}

  /** A new temporary password; {@code random} must be a secure generator. */
  public static String generate(RandomGenerator random) {
    StringBuilder password = new StringBuilder(12);
    for (int group : new int[] {3, 3, 4}) {
      if (!password.isEmpty()) {
        password.append('-');
      }
      for (int i = 0; i < group; i++) {
        password.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
      }
    }
    return password.toString();
  }
}
