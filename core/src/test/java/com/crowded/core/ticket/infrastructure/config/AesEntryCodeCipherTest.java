package com.crowded.core.ticket.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.crowded.core.shared.domain.exception.UnavailableException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AesEntryCodeCipherTest {

  private static String randomKey() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }

  private final AesEntryCodeCipher cipher = new AesEntryCodeCipher(randomKey());

  @Test
  void decryptsWhatItEncrypted() {
    assertThat(cipher.decrypt(cipher.encrypt("QR-1234-ÁÉ"))).isEqualTo("QR-1234-ÁÉ");
  }

  @Test
  void neverStoresTheEntryCodeInClearOrTwiceTheSame() {
    String first = cipher.encrypt("QR-1234");
    String second = cipher.encrypt("QR-1234");

    assertThat(first).isNotEqualTo(second).doesNotContain("QR-1234");
    assertThat(new String(Base64.getDecoder().decode(first), StandardCharsets.UTF_8))
        .doesNotContain("QR-1234");
  }

  @Test
  void refusesToDecryptWithAnotherKey() {
    String encrypted = cipher.encrypt("QR-1234");

    assertThatIllegalStateException()
        .isThrownBy(() -> new AesEntryCodeCipher(randomKey()).decrypt(encrypted));
  }

  @Test
  void refusesTamperedOrShortValues() {
    byte[] bytes = Base64.getDecoder().decode(cipher.encrypt("QR-1234"));
    bytes[bytes.length - 1] ^= 1;

    assertThatIllegalStateException()
        .isThrownBy(() -> cipher.decrypt(Base64.getEncoder().encodeToString(bytes)));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> cipher.decrypt(Base64.getEncoder().encodeToString(new byte[12])));
  }

  @Test
  void acceptsAKeyWithoutTheBase64Padding() {
    String unpadded = randomKey().replace("=", "");

    AesEntryCodeCipher withUnpaddedKey = new AesEntryCodeCipher(unpadded);

    assertThat(withUnpaddedKey.decrypt(withUnpaddedKey.encrypt("QR-1234"))).isEqualTo("QR-1234");
  }

  @Test
  void refusesKeysThatAreNotBase64OrNot256Bits() {
    assertThatIllegalArgumentException().isThrownBy(() -> new AesEntryCodeCipher("not base64!"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new AesEntryCodeCipher(Base64.getEncoder().encodeToString(new byte[16])));
  }

  @Test
  void isUnavailableWithoutAKey() {
    AesEntryCodeCipher noKey = new AesEntryCodeCipher("");

    assertThatExceptionOfType(UnavailableException.class)
        .isThrownBy(() -> noKey.encrypt("QR-1234"))
        .satisfies(e -> assertThat(e.getCode()).isEqualTo("TICKET_IMPORT_UNAVAILABLE"));
    assertThatExceptionOfType(UnavailableException.class)
        .isThrownBy(() -> noKey.decrypt("anything"));
  }
}
