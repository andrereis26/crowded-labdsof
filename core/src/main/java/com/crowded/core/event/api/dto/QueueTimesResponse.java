package com.crowded.core.event.api.dto;

import java.util.List;

/**
 * Response of {@code PUT /events/{eventId}/queues}.
 *
 * @param queues every queue point of the event with a waiting time, in order
 */
public record QueueTimesResponse(List<QueueTimeResponse> queues) {

  public QueueTimesResponse {
    queues = List.copyOf(queues);
  }
}
