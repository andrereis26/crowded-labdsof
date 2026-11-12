package com.crowded.notifications.notification.infrastructure.persistence.mapper;

import com.crowded.notifications.notification.domain.model.ChangeType;
import com.crowded.notifications.notification.domain.model.OfficialChange;
import com.crowded.notifications.notification.infrastructure.persistence.repository.OfficialChangeRow;
import org.mapstruct.Mapper;

/** Maps the official changes read from Core's {@code event_change}. */
@Mapper
public interface OfficialChangePersistenceMapper {

  default OfficialChange toDomain(OfficialChangeRow row) {
    return OfficialChange.restore(
        row.getId(),
        row.getEventId(),
        ChangeType.valueOf(row.getType()),
        row.getStartChanged(),
        row.getDoorsOpenChanged(),
        row.getPreviousVenueName(),
        row.getNewVenueName(),
        row.getNewAddress(),
        row.getTicketStillValid(),
        row.getRefundInfo(),
        row.getMessage());
  }
}
