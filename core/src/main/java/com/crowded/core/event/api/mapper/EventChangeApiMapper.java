package com.crowded.core.event.api.mapper;

import com.crowded.core.event.api.dto.NewValueRequest;
import com.crowded.core.event.api.dto.PublishChangeRequest;
import com.crowded.core.event.api.dto.PublishedChangeResponse;
import com.crowded.core.event.application.dto.PublishChangeCommand;
import com.crowded.core.event.application.dto.PublishedChange;
import com.crowded.core.event.domain.model.NewValues;
import com.crowded.core.event.domain.model.RequestedChange;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps the change endpoint's request and response to and from the application layer. */
@Mapper
public interface EventChangeApiMapper {

  @Mapping(target = "change", source = "request")
  PublishChangeCommand toCommand(UUID eventId, UUID userId, PublishChangeRequest request);

  @Mapping(target = "newValues", source = "newValue")
  RequestedChange toRequestedChange(PublishChangeRequest request);

  NewValues toNewValues(NewValueRequest request);

  PublishedChangeResponse toResponse(PublishedChange change);

  /** The domain compares instants, whatever offset the client sent. */
  default Instant toInstant(OffsetDateTime value) {
    return value == null ? null : value.toInstant();
  }
}
