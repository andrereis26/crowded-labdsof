package com.crowded.core.event.api.mapper;

import com.crowded.core.event.api.dto.CrowdResponse;
import com.crowded.core.event.api.dto.PublishCrowdLevelRequest;
import com.crowded.core.event.application.dto.PublishCrowdLevelCommand;
import com.crowded.core.event.domain.model.CrowdStatus;
import java.util.UUID;
import org.mapstruct.Mapper;

/** Maps the crowd level endpoint's request and response to and from the application layer. */
@Mapper
public interface CrowdLevelApiMapper {

  PublishCrowdLevelCommand toCommand(UUID eventId, UUID userId, PublishCrowdLevelRequest request);

  /** By hand: the response says whether the level is out of date, which only the rule knows. */
  default CrowdResponse toResponse(CrowdStatus crowd, FreshnessView freshness) {
    return freshness.crowd(crowd);
  }
}
