package com.crowded.core.user.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crowded.core.user.domain.model.IssuedToken;
import com.crowded.core.user.domain.repository.TokenRevocations;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

/** The token Core issues is the one its decoder accepts, until the user's tokens are revoked. */
class TokenConfigurationTest {

  private static final UUID USER = UUID.randomUUID();

  @TempDir Path keys;

  private final TokenRevocations revocations = mock(TokenRevocations.class);

  @Test
  void issuesATokenItsDecoderAcceptsUntilItIsRevoked() throws Exception {
    KeyPair pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    FileSystemResource privateKey =
        pem("private.pem", "PRIVATE KEY", pair.getPrivate().getEncoded());
    FileSystemResource publicKey = pem("public.pem", "PUBLIC KEY", pair.getPublic().getEncoded());
    // Real time: the decoder checks the expiry against the system clock.
    Instant now = Instant.now();
    TokenConfiguration configuration = new TokenConfiguration();
    IssuedToken token =
        configuration
            .tokenIssuer(
                privateKey, publicKey, Duration.ofHours(1), Clock.fixed(now, ZoneOffset.UTC))
            .issue(USER);
    JwtDecoder decoder = configuration.jwtDecoder(publicKey, revocations);
    when(revocations.revokedBefore(USER)).thenReturn(Optional.empty());

    Jwt decoded = decoder.decode(token.value());

    assertThat(decoded.getSubject()).isEqualTo(USER.toString());
    assertThat(decoded.getId()).isNotBlank();
    assertThat(decoded.getHeaders()).containsEntry("alg", "RS256");
    assertThat(decoded.<Long>getClaim("iat_ms")).isEqualTo(now.toEpochMilli());
    assertThat(token.expiresAt()).isEqualTo(now.plus(Duration.ofHours(1)));
    assertThat(decoded.getExpiresAt())
        .isEqualTo(Instant.ofEpochSecond(token.expiresAt().getEpochSecond()));

    when(revocations.revokedBefore(USER)).thenReturn(Optional.of(now.plusMillis(1)));
    assertThatThrownBy(() -> decoder.decode(token.value()))
        .isInstanceOf(JwtValidationException.class)
        .hasMessageContaining("revoked");
  }

  private FileSystemResource pem(String name, String type, byte[] der) throws Exception {
    String body =
        Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
    Path file = keys.resolve(name);
    Files.writeString(
        file, "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n");
    return new FileSystemResource(file);
  }
}
