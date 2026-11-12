package com.crowded.notifications.notification.infrastructure.persistence.mapper;

import com.crowded.notifications.notification.domain.model.OfficialChange;
import com.crowded.notifications.notification.infrastructure.persistence.repository.OfficialChangeRow;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.mapstruct.Mapper;

/** Maps the official changes read from Core's {@code event_change}. */
@Mapper
public interface OfficialChangePersistenceMapper {

  OfficialChange toDomain(OfficialChangeRow row);

  /**
   * The instant Core wrote as ISO-8601 text; {@code null} when absent or not an instant, so the
   * notification leaves out a value it cannot read instead of never being sent.
   */
  default Instant toInstant(String text) {
    if (text == null) {
      return null;
    }
    try {
      return Instant.parse(text);
    } catch (DateTimeParseException e) {
      return null;
    }
  }
}
