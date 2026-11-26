package com.crowded.core.event.api.mapper;

import com.crowded.core.connection.api.dto.ActiveAlertResponse;
import com.crowded.core.connection.domain.model.ActiveAlert;
import com.crowded.core.event.api.dto.CrowdResponse;
import com.crowded.core.event.api.dto.EventChangeResponse;
import com.crowded.core.event.api.dto.EventPageResponse;
import com.crowded.core.event.api.dto.ForecastResponse;
import com.crowded.core.event.api.dto.ProvenanceResponse;
import com.crowded.core.event.api.dto.QueueTimeResponse;
import com.crowded.core.event.api.dto.WeatherWarningResponse;
import com.crowded.core.event.application.dto.EventChangeView;
import com.crowded.core.event.application.dto.EventPage;
import com.crowded.core.event.application.dto.WeatherWarningView;
import com.crowded.core.event.domain.model.CrowdStatus;
import com.crowded.core.event.domain.model.InformationKind;
import com.crowded.core.event.domain.model.Provenance;
import com.crowded.core.event.domain.model.QueuePoint;
import com.crowded.core.event.domain.model.WeatherForecast;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/** Maps an event's page and its changes to the participant endpoints' responses. */
@Mapper(imports = Provenance.class)
public interface EventPageApiMapper {

  @Mapping(target = "venue.name", source = "venueName")
  @Mapping(target = "venue.address", source = "address")
  @Mapping(target = "venue.district", source = "district")
  @Mapping(target = "venue.latitude", source = "latitude")
  @Mapping(target = "venue.longitude", source = "longitude")
  @Mapping(target = "organization.id", source = "organizationId")
  @Mapping(target = "organization.name", source = "organizationName")
  @Mapping(target = "weather.forecast", source = "forecast")
  @Mapping(target = "weather.warnings", source = "warnings")
  EventPageResponse toResponse(EventPage page, @Context FreshnessView freshness);

  @Mapping(target = "provenance", qualifiedByName = "crowdProvenance")
  CrowdResponse toResponse(CrowdStatus crowd, @Context FreshnessView freshness);

  @Mapping(target = "provenance", qualifiedByName = "weatherProvenance")
  ForecastResponse toResponse(WeatherForecast forecast, @Context FreshnessView freshness);

  /**
   * A warning holds while IPMA keeps it, until its own end: like an announcement, it never goes out
   * of date.
   */
  WeatherWarningResponse toResponse(WeatherWarningView warning);

  @Mapping(target = "pointName", source = "name")
  @Mapping(target = "waitMinutes", source = "time.waitMinutes")
  @Mapping(target = "provenance", source = "time.provenance", qualifiedByName = "queueProvenance")
  QueueTimeResponse toResponse(QueuePoint queue, @Context FreshnessView freshness);

  /** On-site information, with whether it is out of date at the time of the response. */
  @Named("onSite")
  @Mapping(target = "stale", expression = "java(freshness.isStale(kind, provenance))")
  @Mapping(target = "staleAt", expression = "java(freshness.staleAt(kind, provenance))")
  ProvenanceResponse onSite(
      Provenance provenance, @Context InformationKind kind, @Context FreshnessView freshness);

  @Named("crowdProvenance")
  default ProvenanceResponse crowdProvenance(
      Provenance provenance, @Context FreshnessView freshness) {
    return onSite(provenance, InformationKind.CROWD, freshness);
  }

  @Named("queueProvenance")
  default ProvenanceResponse queueProvenance(
      Provenance provenance, @Context FreshnessView freshness) {
    return onSite(provenance, InformationKind.QUEUE, freshness);
  }

  @Named("weatherProvenance")
  default ProvenanceResponse weatherProvenance(
      Provenance provenance, @Context FreshnessView freshness) {
    return onSite(provenance, InformationKind.WEATHER, freshness);
  }

  /** An announcement, such as an official change or an alert: it never goes out of date. */
  @Mapping(target = "stale", constant = "false")
  @Mapping(target = "staleAt", ignore = true)
  ProvenanceResponse toResponse(Provenance provenance);

  @Mapping(target = "id", source = "alertId")
  @Mapping(
      target = "provenance",
      expression =
          "java(toResponse(Provenance.organization(alert.organizationName(), alert.sentAt())))")
  ActiveAlertResponse toResponse(ActiveAlert alert);

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
