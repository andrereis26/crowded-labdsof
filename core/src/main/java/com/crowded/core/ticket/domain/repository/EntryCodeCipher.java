package com.crowded.core.ticket.domain.repository;

/** Encrypts a ticket's entry code, which is a credential, so it is never stored in clear. */
public interface EntryCodeCipher {

  /**
   * @throws com.crowded.core.shared.domain.exception.UnavailableException when no key is configured
   */
  String encrypt(String entryCode);

  /**
   * The entry code that {@link #encrypt} gave {@code encrypted} for.
   *
   * @throws com.crowded.core.shared.domain.exception.UnavailableException when no key is configured
   */
  String decrypt(String encrypted);
}
