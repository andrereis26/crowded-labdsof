package com.crowded.core.user.infrastructure.persistence.repository;

import com.crowded.core.shared.domain.exception.UnavailableException;
import com.crowded.core.user.domain.model.Session;
import com.crowded.core.user.domain.repository.Sessions;
import com.crowded.core.user.infrastructure.persistence.entity.SessionEntity;
import com.crowded.core.user.infrastructure.persistence.mapper.SessionPersistenceMapper;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link Sessions} in Redis (ADR 0021): {@code session:<id>} holds the session as JSON and expires
 * with it, and {@code user-sessions:<userId>} lists a user's sessions, so all of them can end at
 * once.
 */
@Repository
class SessionsRepositoryAdapter implements Sessions {

  static final String PREFIX = "session:";
  static final String USER_PREFIX = "user-sessions:";

  private final StringRedisTemplate redis;
  private final JsonMapper json;
  private final SessionPersistenceMapper mapper;
  private final Clock clock;

  SessionsRepositoryAdapter(
      StringRedisTemplate redis, JsonMapper json, SessionPersistenceMapper mapper, Clock clock) {
    this.redis = redis;
    this.json = json;
    this.mapper = mapper;
    this.clock = clock;
  }

  @Override
  public void save(Session session) {
    String key = PREFIX + session.getId();
    String userKey = USER_PREFIX + session.getUserId();
    Duration timeToLive = Duration.between(clock.instant(), session.expiresAt());
    try {
      if (session.hasEnded() || timeToLive.compareTo(Duration.ofSeconds(1)) < 0) {
        redis.delete(key);
        redis.opsForSet().remove(userKey, session.getId().toString());
        return;
      }
      redis.opsForValue().set(key, json.writeValueAsString(mapper.toEntity(session)), timeToLive);
      redis.opsForSet().add(userKey, session.getId().toString());
      // The list lives as long as the longest session in it.
      Duration listTimeToLive = Duration.between(clock.instant(), session.getEndsAt());
      Long current = redis.getExpire(userKey);
      if (current == null || current < listTimeToLive.toSeconds()) {
        redis.expire(userKey, listTimeToLive);
      }
    } catch (DataAccessException e) {
      throw unavailable();
    }
  }

  @Override
  public Optional<Session> findById(UUID sessionId) {
    try {
      return Optional.ofNullable(redis.opsForValue().get(PREFIX + sessionId))
          .map(value -> mapper.toDomain(json.readValue(value, SessionEntity.class)));
    } catch (DataAccessException e) {
      throw unavailable();
    }
  }

  @Override
  public void endAllOf(UUID userId) {
    String userKey = USER_PREFIX + userId;
    try {
      Set<String> ids = redis.opsForSet().members(userKey);
      if (ids != null) {
        ids.forEach(id -> redis.delete(PREFIX + id));
      }
      redis.delete(userKey);
    } catch (DataAccessException e) {
      throw unavailable();
    }
  }

  private static UnavailableException unavailable() {
    return new UnavailableException(
        "SESSIONS_UNAVAILABLE", "This is not available right now. Try again soon.");
  }
}
