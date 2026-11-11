package com.crowded.core.user.application.port;

import com.crowded.core.user.application.dto.IssuedToken;
import java.util.UUID;

/** Issues the short-lived access tokens every service validates (ADR 0005). */
public interface TokenIssuer {

  /** A signed token whose subject is {@code userId}. */
  IssuedToken issue(UUID userId);
}
