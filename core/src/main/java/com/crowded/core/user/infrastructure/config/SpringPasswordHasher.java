package com.crowded.core.user.infrastructure.config;

import com.crowded.core.user.domain.repository.PasswordHasher;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * bcrypt, in the {@code {id}} format of Spring Security's delegating encoder, as the demo hashes
 * (database/seed): another algorithm can be added later without rehashing every password.
 */
@Component
class SpringPasswordHasher implements PasswordHasher {

  private final PasswordEncoder encoder =
      PasswordEncoderFactories.createDelegatingPasswordEncoder();

  @Override
  public String hash(String password) {
    return encoder.encode(password);
  }

  @Override
  public boolean matches(String password, String hash) {
    return encoder.matches(password, hash);
  }
}
