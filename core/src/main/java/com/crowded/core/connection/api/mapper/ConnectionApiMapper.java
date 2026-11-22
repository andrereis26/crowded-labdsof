package com.crowded.core.connection.api.mapper;

import com.crowded.core.connection.api.dto.ConnectionResponse;
import com.crowded.core.connection.api.dto.MyConnectionsResponse;
import com.crowded.core.connection.application.dto.JoinWithAccessCodeCommand;
import com.crowded.core.connection.application.dto.JoinedEvent;
import com.crowded.core.connection.application.dto.MyConnections;
import com.crowded.core.event.api.mapper.EventPageApiMapper;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps the connection endpoints' requests and responses to and from the application layer. */
@Mapper(uses = EventPageApiMapper.class)
public interface ConnectionApiMapper {

  JoinWithAccessCodeCommand toCommand(UUID userId, String accessCode);

  @Mapping(target = "event.id", source = "eventId")
  @Mapping(target = "event.name", source = "name")
  @Mapping(target = "event.startDateTime", source = "startDateTime")
  @Mapping(target = "event.doorsOpenAt", source = "doorsOpenAt")
  @Mapping(target = "event.venueName", source = "venueName")
  ConnectionResponse toResponse(JoinedEvent joined);

  MyConnectionsResponse toResponse(MyConnections connections);
}
