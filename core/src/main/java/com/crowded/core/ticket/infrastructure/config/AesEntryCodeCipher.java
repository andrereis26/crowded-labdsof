package com.crowded.core.ticket.infrastructure.config;

import com.crowded.core.shared.domain.exception.UnavailableException;
import com.crowded.core.ticket.domain.repository.EntryCodeCipher;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Encrypts entry codes with AES-256-GCM: Base64 of a random 12-byte IV followed by the ciphertext
 * and its tag. The key is {@code crowded.tickets.entry-code-key}; without it, Core starts but
 * refuses ticket imports, so no entry code is ever stored in clear.
 */
@Component
final class AesEntryCodeCipher implements EntryCodeCipher {

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int KEY_BYTES = 32;
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;

  private final SecretKey key;
  private final SecureRandom random = new SecureRandom();

  /**
   * @param base64Key a 256-bit key in Base64, padded or not, or empty when none is configured
   * @throws IllegalArgumentException when the key is set but is not a 256-bit key
   */
  AesEntryCodeCipher(@Value("${crowded.tickets.entry-code-key:}") String base64Key) {
    this.key = base64Key == null || base64Key.isBlank() ? null : parse(base64Key.strip());
  }

  private static SecretKey parse(String base64Key) {
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(base64Key);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("crowded.tickets.entry-code-key is not valid Base64", e);
    }
    if (bytes.length != KEY_BYTES) {
      throw new IllegalArgumentException("crowded.tickets.entry-code-key must have 256 bits");
    }
    return new SecretKeySpec(bytes, "AES");
  }

  @Override
  public String encrypt(String entryCode) {
    SecretKey secret = requireKey();
    byte[] iv = new byte[IV_BYTES];
    random.nextBytes(iv);
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, secret, new GCMParameterSpec(TAG_BITS, iv));
      byte[] encrypted = cipher.doFinal(entryCode.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder()
          .encodeToString(
              ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Could not encrypt the entry code", e);
    }
  }

  @Override
  public String decrypt(String encrypted) {
    SecretKey secret = requireKey();
    byte[] bytes = Base64.getDecoder().decode(encrypted);
    if (bytes.length <= IV_BYTES) {
      throw new IllegalArgumentException("Not an encrypted entry code");
    }
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, secret, new GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES));
      return new String(
          cipher.doFinal(bytes, IV_BYTES, bytes.length - IV_BYTES), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Could not decrypt the entry code", e);
    }
  }

  private SecretKey requireKey() {
    if (key == null) {
      throw new UnavailableException(
          "TICKET_IMPORT_UNAVAILABLE", "This is not available right now. Try again soon.");
    }
    return key;
  }
}
