package com.crowded.core.user.infrastructure.security;

import com.crowded.core.user.application.port.TokenIssuer;
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
      @Value("${crowded.jwt.lifetime:1h}") Duration lifetime,
      Clock clock)
      throws IOException {
    RSAPrivateKey signing;
    try (InputStream in = privateKey.getInputStream()) {
      signing = RsaKeyConverters.pkcs8().convert(in);
    }
    RSAPublicKey verifying;
    try (InputStream in = publicKey.getInputStream()) {
      verifying = RsaKeyConverters.x509().convert(in);
    }
    return new JwtTokenIssuer(
        NimbusJwtEncoder.withKeyPair(verifying, signing).build(), clock, lifetime);
  }
}
