package com.crowded.core.user.infrastructure.persistence.mapper;

import com.crowded.core.user.domain.model.Session;
import com.crowded.core.user.infrastructure.persistence.entity.SessionEntity;
import org.mapstruct.Mapper;

/** Maps sessions to the documents stored in Redis, and back. */
@Mapper
public interface SessionPersistenceMapper {

  SessionEntity toEntity(Session session);

  default Session toDomain(SessionEntity entity) {
    return Session.restore(
        entity.id(),
        entity.userId(),
        entity.startedAt(),
        entity.endsAt(),
        entity.idleLifetime(),
        entity.tokenHash(),
        entity.previousTokenHash(),
        entity.rotatedAt(),
        entity.lastUsedAt());
  }
}
