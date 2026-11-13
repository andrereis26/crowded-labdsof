package com.crowded.core.event.api.mapper;

import com.crowded.core.event.api.dto.EventChangeResponse;
import com.crowded.core.event.api.dto.EventPageResponse;
import com.crowded.core.event.api.dto.ProvenanceResponse;
import com.crowded.core.event.application.dto.EventChangeView;
import com.crowded.core.event.application.dto.EventPage;
import com.crowded.core.event.domain.model.Provenance;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps an event's page and its changes to the participant endpoints' responses. */
@Mapper
public interface EventPageApiMapper {

  @Mapping(target = "venue.name", source = "venueName")
  @Mapping(target = "venue.address", source = "address")
  @Mapping(target = "venue.district", source = "district")
  @Mapping(target = "venue.latitude", source = "latitude")
  @Mapping(target = "venue.longitude", source = "longitude")
  @Mapping(target = "organization.id", source = "organizationId")
  @Mapping(target = "organization.name", source = "organizationName")
  EventPageResponse toResponse(EventPage page);

  ProvenanceResponse toResponse(Provenance provenance);

  /** By hand: a change always has both maps, which the response copies. */
  default EventChangeResponse toResponse(EventChangeView change) {
    if (change == null) {
      return null;
    }
    return new EventChangeResponse(
        change.id(),
        change.type(),
        change.severity(),
        change.previousValue(),
        change.newValue(),
        change.ticketStillValid(),
        change.refundInfo(),
        change.message(),
        toResponse(change.provenance()));
  }
}
