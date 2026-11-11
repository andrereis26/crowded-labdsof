package com.crowded.core.user.infrastructure.security;

import com.crowded.core.user.application.dto.IssuedToken;
import com.crowded.core.user.application.port.TokenIssuer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/**
 * RS256 tokens signed with Core's private key (ADR 0005). The subject is the user id and nothing
 * else: every service reads the role and organization from the database (API Outline). The id
 * ({@code jti}) lets a token be revoked before it expires (ADR 0004).
 */
class JwtTokenIssuer implements TokenIssuer {

  private final JwtEncoder encoder;
  private final Clock clock;
  private final Duration lifetime;

  JwtTokenIssuer(JwtEncoder encoder, Clock clock, Duration lifetime) {
    this.encoder = encoder;
    this.clock = clock;
    this.lifetime = lifetime;
  }

  @Override
  public IssuedToken issue(UUID userId) {
    Instant now = clock.instant();
    Instant expiresAt = now.plus(lifetime);
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .id(UUID.randomUUID().toString())
            .subject(userId.toString())
            .issuedAt(now)
            .expiresAt(expiresAt)
            .build();
    String value =
        encoder
            .encode(
                JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
            .getTokenValue();
    return new IssuedToken(value, expiresAt);
  }
}
