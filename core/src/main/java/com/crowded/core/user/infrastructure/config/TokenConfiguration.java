package com.crowded.core.user.infrastructure.config;

import com.crowded.core.user.domain.model.SessionLifetime;
import com.crowded.core.user.domain.model.SessionLifetimes;
import com.crowded.core.user.domain.repository.TokenIssuer;
import com.crowded.core.user.domain.repository.TokenRevocations;
import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * The token issuer, with the key pair Core signs with: the private key that only Core gets, and the
 * public key every service validates against (ADR 0005).
 */
@Configuration(proxyBeanMethods = false)
class TokenConfiguration {

  @Bean
  TokenIssuer tokenIssuer(
      @Value("${crowded.jwt.private-key-location}") Resource privateKey,
      @Value("${spring.security.oauth2.resourceserver.jwt.public-key-location}") Resource publicKey,
      @Value("${crowded.jwt.lifetime:15m}") Duration lifetime,
      Clock clock)
      throws IOException {
    RSAPrivateKey signing;
    try (InputStream in = privateKey.getInputStream()) {
      signing = RsaKeyConverters.pkcs8().convert(in);
    }
    RSAPublicKey verifying = publicKey(publicKey);
    return new JwtTokenIssuer(
        NimbusJwtEncoder.withKeyPair(verifying, signing).build(), clock, lifetime);
  }

  /** How long sessions last, by role (ADR 0021). */
  @Bean
  SessionLifetimes sessionLifetimes(
      @Value("${crowded.sessions.participant.idle:30d}") Duration participantIdle,
      @Value("${crowded.sessions.participant.maximum:90d}") Duration participantMaximum,
      @Value("${crowded.sessions.backoffice.idle:12h}") Duration backofficeIdle,
      @Value("${crowded.sessions.backoffice.maximum:7d}") Duration backofficeMaximum) {
    return new SessionLifetimes(
        new SessionLifetime(participantIdle, participantMaximum),
        new SessionLifetime(backofficeIdle, backofficeMaximum));
  }

  /**
   * Checks every token's signature, expiry and revocation (ADR 0004, ADR 0005). Replaces the
   * decoder Spring Boot would build from the same public key.
   */
  @Bean
  JwtDecoder jwtDecoder(
      @Value("${spring.security.oauth2.resourceserver.jwt.public-key-location}") Resource publicKey,
      TokenRevocations revocations)
      throws IOException {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey(publicKey)).build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefault(), new RevokedTokenValidator(revocations)));
    return decoder;
  }

  private static RSAPublicKey publicKey(Resource location) throws IOException {
    try (InputStream in = location.getInputStream()) {
      return RsaKeyConverters.x509().convert(in);
    }
  }
}
