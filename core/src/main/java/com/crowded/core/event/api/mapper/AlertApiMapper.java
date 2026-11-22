package com.crowded.core.event.api.mapper;

import com.crowded.core.event.api.dto.SendAlertRequest;
import com.crowded.core.event.api.dto.SentAlertResponse;
import com.crowded.core.event.application.dto.SendAlertCommand;
import com.crowded.core.event.domain.model.Alert;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps the alerts endpoint's request and response to and from the application layer. */
@Mapper
public interface AlertApiMapper {

  SendAlertCommand toCommand(UUID eventId, UUID userId, SendAlertRequest request);

  @Mapping(target = "recipientsCount", source = "recipients")
  SentAlertResponse toResponse(Alert alert);
}
