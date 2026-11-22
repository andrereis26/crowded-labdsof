package com.crowded.core.event.api.mapper;

import com.crowded.core.connection.api.dto.ActiveAlertResponse;
import com.crowded.core.connection.domain.model.ActiveAlert;
import com.crowded.core.event.api.dto.CrowdResponse;
import com.crowded.core.event.api.dto.EventChangeResponse;
import com.crowded.core.event.api.dto.EventPageResponse;
import com.crowded.core.event.api.dto.ProvenanceResponse;
import com.crowded.core.event.api.dto.QueueTimeResponse;
import com.crowded.core.event.application.dto.EventChangeView;
import com.crowded.core.event.application.dto.EventPage;
import com.crowded.core.event.domain.model.CrowdStatus;
import com.crowded.core.event.domain.model.Provenance;
import com.crowded.core.event.domain.model.QueuePoint;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps an event's page and its changes to the participant endpoints' responses. */
@Mapper(imports = {Provenance.class, FreshnessView.class})
public interface EventPageApiMapper {

  @Mapping(target = "venue.name", source = "venueName")
  @Mapping(target = "venue.address", source = "address")
  @Mapping(target = "venue.district", source = "district")
  @Mapping(target = "venue.latitude", source = "latitude")
  @Mapping(target = "venue.longitude", source = "longitude")
  @Mapping(target = "organization.id", source = "organizationId")
  @Mapping(target = "organization.name", source = "organizationName")
  EventPageResponse toResponse(EventPage page, @Context FreshnessView freshness);

  /** By hand: the crowd level says whether it is out of date, which only the rule knows. */
  default CrowdResponse toResponse(CrowdStatus crowd, @Context FreshnessView freshness) {
    return freshness.crowd(crowd);
  }

  /** An official change is an announcement, not on-site information: it never goes out of date. */
  default ProvenanceResponse toResponse(Provenance provenance) {
    return FreshnessView.announced(provenance);
  }

  @Mapping(target = "id", source = "alertId")
  @Mapping(
      target = "provenance",
      expression =
          "java(FreshnessView.announced(Provenance.organization(alert.organizationName(), alert.sentAt())))")
  ActiveAlertResponse toResponse(ActiveAlert alert);

  /** By hand: a waiting time says whether it is out of date, which only the rule knows. */
  default QueueTimeResponse toResponse(QueuePoint queue, @Context FreshnessView freshness) {
    return freshness.queue(queue);
  }

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
