package com.crowded.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * An RSA key pair made for one test run and written as PEM files, so no key is committed. It signs
 * the tokens of the integration tests, as Core will once it issues them.
 */
record TestJwtKeys(Path publicKey, Path privateKey, KeyPair pair) {

  static TestJwtKeys generate() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      KeyPair pair = generator.generateKeyPair();
      Path publicKey = Files.createTempFile("jwt-public", ".pem");
      publicKey.toFile().deleteOnExit();
      Files.writeString(publicKey, pem("PUBLIC KEY", pair.getPublic().getEncoded()));
      Path privateKey = Files.createTempFile("jwt-private", ".pem");
      privateKey.toFile().deleteOnExit();
      Files.writeString(privateKey, pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
      return new TestJwtKeys(publicKey, privateKey, pair);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Location of the public key, as {@code file:} URL. */
  String publicKeyLocation() {
    return publicKey.toUri().toString();
  }

  /** Location of the private key, as {@code file:} URL. */
  String privateKeyLocation() {
    return privateKey.toUri().toString();
  }

  /** A signed token for the user {@code subject}, valid for five minutes. */
  String token(String subject) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(subject)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .build();
    return NimbusJwtEncoder.withKeyPair(
            (RSAPublicKey) pair.getPublic(), (RSAPrivateKey) pair.getPrivate())
        .build()
        .encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
        .getTokenValue();
  }

  private static String pem(String type, byte[] der) {
    String body =
        Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
    return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
  }
}
