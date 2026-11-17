package com.crowded.core.ticket.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crowded.core.shared.domain.exception.UnavailableException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HmacEntryCodeHasherTest {

  private static final UUID EVENT = UUID.randomUUID();

  private static String newKey() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }

  @Test
  void givesTheSameHashForTheSameTicketOfAnEvent() {
    HmacEntryCodeHasher hasher = new HmacEntryCodeHasher(newKey());

    assertThat(hasher.hash(EVENT, "QR-1")).isEqualTo(hasher.hash(EVENT, "QR-1"));
  }

  @Test
  void givesAnotherHashForAnotherCodeOrEvent() {
    HmacEntryCodeHasher hasher = new HmacEntryCodeHasher(newKey());

    assertThat(hasher.hash(EVENT, "QR-1")).isNotEqualTo(hasher.hash(EVENT, "QR-2"));
    assertThat(hasher.hash(EVENT, "QR-1")).isNotEqualTo(hasher.hash(UUID.randomUUID(), "QR-1"));
  }

  @Test
  void dependsOnTheKeyAndDoesNotShowTheCode() {
    String hash = new HmacEntryCodeHasher(newKey()).hash(EVENT, "QR-1").value();

    assertThat(hash).matches("[0-9a-f]{64}").doesNotContain("QR-1");
    assertThat(new HmacEntryCodeHasher(newKey()).hash(EVENT, "QR-1").value()).isNotEqualTo(hash);
  }

  @Test
  void refusesToHashWithoutAKey() {
    assertThatThrownBy(() -> new HmacEntryCodeHasher("").hash(EVENT, "QR-1"))
        .isInstanceOfSatisfying(
            UnavailableException.class,
            e -> assertThat(e.getCode()).isEqualTo("TICKET_IMPORT_UNAVAILABLE"));
  }

  @Test
  void refusesAKeyThatIsNotBase64() {
    assertThatThrownBy(() -> new HmacEntryCodeHasher("not base64!"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
