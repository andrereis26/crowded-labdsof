package com.crowded.core.event.application.dto;

import com.crowded.core.event.domain.model.QueueWait;
import java.util.List;
import java.util.UUID;

/**
 * Publish the waiting times {@code queues} at the event's queue points on behalf of {@code userId}.
 *
 * @param userId the signed-in user, from the token's subject
 */
public record PublishQueueTimesCommand(UUID eventId, UUID userId, List<QueueWait> queues) {

  public PublishQueueTimesCommand {
    queues = List.copyOf(queues);
  }
}
