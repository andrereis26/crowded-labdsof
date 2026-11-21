package com.crowded.core.event.api.mapper;

import com.crowded.core.event.api.dto.ConfirmationsResponse;
import com.crowded.core.event.api.dto.CorrectEventRequest;
import com.crowded.core.event.api.dto.CreateEventRequest;
import com.crowded.core.event.api.dto.ManagedEventResponse;
import com.crowded.core.event.api.dto.QueueTimeResponse;
import com.crowded.core.event.api.dto.VenueResponse;
import com.crowded.core.event.application.dto.CorrectEventCommand;
import com.crowded.core.event.application.dto.ManagedEvent;
import com.crowded.core.event.application.dto.RegisterEventCommand;
import com.crowded.core.event.domain.model.ConfirmationSettings;
import com.crowded.core.event.domain.model.District;
import com.crowded.core.event.domain.model.EventState;
import com.crowded.core.event.domain.model.EventType;
import com.crowded.core.event.domain.model.QueuePoint;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/** Maps the event endpoints' requests and responses to and from the application layer. */
@Mapper(uses = EventPageApiMapper.class)
public interface EventApiMapper {

  @Mapping(target = "code", source = "request", qualifiedByName = "codeOfItsType")
  RegisterEventCommand toCommand(UUID userId, CreateEventRequest request);

  CorrectEventCommand toCommand(UUID userId, UUID eventId, CorrectEventRequest request);

  /** The field of the event's type: the code printed on its tickets, or its access code. */
  @Named("codeOfItsType")
  default String codeOfItsType(CreateEventRequest request) {
    return request.type() == EventType.TICKETED ? request.ticketEventCode() : request.accessCode();
  }

  @Mapping(target = "id", source = "event.id")
  @Mapping(target = "name", source = "event.profile.name")
  @Mapping(target = "description", source = "event.profile.description")
  @Mapping(target = "type", source = "event.admission.type")
  @Mapping(target = "status", source = "event.state.status")
  @Mapping(
      target = "ticketEventCode",
      expression = "java(managed.event().getAdmission().ticketEventCode())")
  @Mapping(target = "accessCode", expression = "java(managed.event().getAdmission().accessCode())")
  @Mapping(target = "startDateTime", source = "event.state.schedule.start")
  @Mapping(target = "doorsOpenAt", source = "event.state.schedule.doorsOpenAt")
  @Mapping(target = "endDateTime", source = "event.state.schedule.end")
  @Mapping(
      target = "venue",
      expression = "java(toVenue(managed.event().getState(), managed.event().getDistrict()))")
  @Mapping(target = "capacity", source = "event.capacity.people")
  @Mapping(target = "queuePoints", expression = "java(managed.event().getQueuePoints().names())")
  @Mapping(target = "confirmations", source = "event.confirmations")
  @Mapping(target = "crowd", source = "event.crowd")
  @Mapping(
      target = "queues",
      expression = "java(toQueues(managed.event().getQueuePoints().timed()))")
  ManagedEventResponse toResponse(ManagedEvent managed);

  List<QueueTimeResponse> toQueues(List<QueuePoint> queues);

  default VenueResponse toVenue(EventState state, District district) {
    var venue = state.venue();
    var coordinates = venue.coordinates();
    return new VenueResponse(
        venue.name(),
        venue.address(),
        district.name(),
        coordinates == null ? null : coordinates.latitude(),
        coordinates == null ? null : coordinates.longitude());
  }

  /** The categories in a stable order, so the response does not change between calls. */
  default ConfirmationsResponse toConfirmations(ConfirmationSettings settings) {
    return new ConfirmationsResponse(
        settings.promptCategories().stream().sorted().toList(),
        settings.rules().venueRadiusMeters(),
        settings.rules().highConfidenceAnswers(),
        settings.rules().maxPromptsPerHour());
  }

  /** The domain compares instants, whatever offset the client sent. */
  default Instant toInstant(OffsetDateTime value) {
    return value == null ? null : value.toInstant();
  }
}
