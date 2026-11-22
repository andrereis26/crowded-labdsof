package com.crowded.core.event.api.mapper;

import com.crowded.core.event.api.dto.PublishQueueTimesRequest;
import com.crowded.core.event.api.dto.QueueTimesResponse;
import com.crowded.core.event.application.dto.PublishQueueTimesCommand;
import com.crowded.core.event.domain.model.QueuePoint;
import java.util.List;
import java.util.UUID;
import org.mapstruct.Mapper;

/** Maps the queue times endpoint's request and response to and from the application layer. */
@Mapper
public interface QueueTimeApiMapper {

  PublishQueueTimesCommand toCommand(UUID eventId, UUID userId, PublishQueueTimesRequest request);

  /** By hand: each waiting time says whether it is out of date, which only the rule knows. */
  default QueueTimesResponse toResponse(List<QueuePoint> queues, FreshnessView freshness) {
    return new QueueTimesResponse(queues.stream().map(freshness::queue).toList());
  }
}
