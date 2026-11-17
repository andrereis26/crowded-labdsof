package com.crowded.core.ticket.infrastructure.config;

import com.crowded.core.shared.domain.exception.UnavailableException;
import com.crowded.core.ticket.domain.model.EntryCodeHash;
import com.crowded.core.ticket.domain.repository.EntryCodeHasher;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Hashes entry codes with HMAC-SHA256 over the event and the code, as hex. Its key is derived from
 * {@code crowded.tickets.entry-code-key} for this one use, so it is never the key that encrypts the
 * codes; without the key, Core refuses ticket imports.
 */
@Component
final class HmacEntryCodeHasher implements EntryCodeHasher {

  private static final String ALGORITHM = "HmacSHA256";
  private static final String KEY_PURPOSE = "entry-code-hash";

  private final SecretKey key;

  HmacEntryCodeHasher(@Value("${crowded.tickets.entry-code-key:}") String base64Key) {
    this.key = base64Key == null || base64Key.isBlank() ? null : derive(base64Key.strip());
  }

  private static SecretKey derive(String base64Key) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(Base64.getDecoder().decode(base64Key), ALGORITHM));
      return new SecretKeySpec(
          mac.doFinal(KEY_PURPOSE.getBytes(StandardCharsets.UTF_8)), ALGORITHM);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new IllegalArgumentException("crowded.tickets.entry-code-key is not usable", e);
    }
  }

  @Override
  public EntryCodeHash hash(UUID eventId, String entryCode) {
    if (key == null) {
      throw new UnavailableException(
          "TICKET_IMPORT_UNAVAILABLE", "This is not available right now. Try again soon.");
    }
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(key);
      byte[] hash = mac.doFinal((eventId + ":" + entryCode).getBytes(StandardCharsets.UTF_8));
      return new EntryCodeHash(HexFormat.of().formatHex(hash));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Could not hash the entry code", e);
    }
  }
}
