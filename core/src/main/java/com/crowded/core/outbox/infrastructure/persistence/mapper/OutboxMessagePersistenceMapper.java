package com.crowded.core.outbox.infrastructure.persistence.mapper;

import com.crowded.core.outbox.domain.model.OutboxMessage;
import com.crowded.core.outbox.infrastructure.persistence.entity.OutboxMessageEntity;
import org.mapstruct.Mapper;

/** Maps outbox messages to and from {@code outbox} rows. */
@Mapper
public interface OutboxMessagePersistenceMapper {

  OutboxMessageEntity toEntity(OutboxMessage message);

  OutboxMessage toDomain(OutboxMessageEntity entity);
}
