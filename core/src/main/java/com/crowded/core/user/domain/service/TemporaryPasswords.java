package com.crowded.core.user.domain.service;

import java.util.random.RandomGenerator;

/**
 * Temporary passwords an administrator gives a member, such as {@code T7q-mK4-v9Px}: ten random
 * characters without the ones easily confused when read aloud or copied (0, O, 1, l, I).
 */
public final class TemporaryPasswords {

  static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

  private TemporaryPasswords() {}

  static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
  static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
  static final String DIGITS = "23456789";

  /**
   * A new temporary password; {@code random} must be a secure generator. It has an uppercase
   * letter, a lowercase letter and a digit in random places, and the hyphens are its special
   * characters, so it meets the same rule as a password a user chooses.
   */
  public static String generate(RandomGenerator random) {
    char[] chars = new char[10];
    chars[0] = pick(UPPER, random);
    chars[1] = pick(LOWER, random);
    chars[2] = pick(DIGITS, random);
    for (int i = 3; i < chars.length; i++) {
      chars[i] = pick(ALPHABET, random);
    }
    for (int i = chars.length - 1; i > 0; i--) {
      int j = random.nextInt(i + 1);
      char swap = chars[i];
      chars[i] = chars[j];
      chars[j] = swap;
    }
    String all = new String(chars);
    return all.substring(0, 3) + "-" + all.substring(3, 6) + "-" + all.substring(6);
  }

  private static char pick(String from, RandomGenerator random) {
    return from.charAt(random.nextInt(from.length()));
  }
}
