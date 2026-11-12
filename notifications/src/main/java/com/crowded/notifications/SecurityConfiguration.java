package com.crowded.notifications;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPublicKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Every request needs a valid, unrevoked JWT (ADR 0004, ADR 0005), except the health endpoints,
 * which the container health check and Docker Swarm call without one. Only health is exposed over
 * HTTP.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(server -> server.jwt(Customizer.withDefaults()));
    return http.build();
  }

  /**
   * Checks every token's signature, expiry and revocation (ADR 0004, ADR 0005). Replaces the
   * decoder Spring Boot would build from the same public key.
   */
  @Bean
  JwtDecoder jwtDecoder(
      @Value("${spring.security.oauth2.resourceserver.jwt.public-key-location}") Resource publicKey,
      StringRedisTemplate redis)
      throws IOException {
    RSAPublicKey key;
    try (InputStream in = publicKey.getInputStream()) {
      key = RsaKeyConverters.x509().convert(in);
    }
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key).build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefault(), new RevokedTokenValidator(redis)));
    return decoder;
  }
}
