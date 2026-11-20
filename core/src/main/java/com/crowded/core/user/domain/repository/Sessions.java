package com.crowded.core.user.domain.repository;

import com.crowded.core.user.domain.model.Session;
import java.util.Optional;
import java.util.UUID;

/**
 * Signed-in sessions (ADR 0021), kept until they expire.
 *
 * <p>Every method throws {@link com.crowded.core.shared.domain.exception.UnavailableException} when
 * the sessions cannot be read or stored.
 */
public interface Sessions {

  /** Stores the session until it expires, or forgets it once it {@link Session#hasEnded()}. */
  void save(Session session);

  /**
   * Stores a refreshed session only while the stored one still has {@code readTokenHash}, the token
   * hash it was read with, so two refreshes at once cannot both win.
   *
   * @return false when another refresh changed the session meanwhile, or it ended
   */
  boolean replace(Session session, String readTokenHash);

  Optional<Session> findById(UUID sessionId);

  /** Ends every session of {@code userId}, such as when their account is deactivated. */
  void endAllOf(UUID userId);
}
