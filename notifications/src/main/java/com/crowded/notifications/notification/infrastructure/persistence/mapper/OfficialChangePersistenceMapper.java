package com.crowded.notifications.notification.infrastructure.persistence.mapper;

import com.crowded.notifications.notification.domain.model.OfficialChange;
import com.crowded.notifications.notification.infrastructure.persistence.repository.OfficialChangeRow;
import org.mapstruct.Mapper;

/** Maps the official changes read from Core's {@code event_change}. */
@Mapper
public interface OfficialChangePersistenceMapper {

  OfficialChange toDomain(OfficialChangeRow row);
}
