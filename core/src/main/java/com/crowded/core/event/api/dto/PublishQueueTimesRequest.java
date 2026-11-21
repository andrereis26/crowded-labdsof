package com.crowded.core.event.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Body of {@code PUT /events/{eventId}/queues} (API Outline, journey 5).
 *
 * @param queues the times to publish; the points left out keep theirs
 */
public record PublishQueueTimesRequest(@NotEmpty List<@NotNull @Valid QueueTimeRequest> queues) {

  public PublishQueueTimesRequest {
    queues = queues == null ? null : List.copyOf(queues);
  }
}
