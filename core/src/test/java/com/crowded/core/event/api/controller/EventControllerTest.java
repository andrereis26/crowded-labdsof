package com.crowded.core.event.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crowded.core.event.api.mapper.EventApiMapperImpl;
import com.crowded.core.event.application.dto.CorrectEventCommand;
import com.crowded.core.event.application.dto.ManagedEvent;
import com.crowded.core.event.application.dto.RegisterEventCommand;
import com.crowded.core.event.application.service.EventManagementService;
import com.crowded.core.event.domain.model.Admission;
import com.crowded.core.event.domain.model.Capacity;
import com.crowded.core.event.domain.model.ConfirmationRules;
import com.crowded.core.event.domain.model.ConfirmationSettings;
import com.crowded.core.event.domain.model.Coordinates;
import com.crowded.core.event.domain.model.District;
import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.EventCode;
import com.crowded.core.event.domain.model.EventProfile;
import com.crowded.core.event.domain.model.EventState;
import com.crowded.core.event.domain.model.EventStatus;
import com.crowded.core.event.domain.model.EventType;
import com.crowded.core.event.domain.model.QueuePoints;
import com.crowded.core.event.domain.model.Schedule;
import com.crowded.core.event.domain.model.Venue;
import com.crowded.core.shared.domain.exception.ConflictException;
import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.shared.infrastructure.config.SecurityConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// The key location only has to resolve: the mocked decoder below replaces the one built from it.
@WebMvcTest(
    controllers = EventController.class,
    properties = "JWT_PUBLIC_KEY_LOCATION=file:unused.pem")
@Import({SecurityConfiguration.class, EventApiMapperImpl.class})
class EventControllerTest {

  private static final UUID USER = UUID.randomUUID();
  private static final Instant START = Instant.parse("2026-12-12T21:00:00Z");
  private static final String CREATE =
      """
      {"name": "São João no Parque", "description": "Concerto gratuito.", "type": "PUBLIC",
       "ticketEventCode": "IGNORED", "accessCode": "SAOJOAO26",
       "startDateTime": "2026-12-12T21:00:00+00:00", "doorsOpenAt": "2026-12-12T20:00:00+00:00",
       "endDateTime": "2026-12-13T02:00:00+00:00",
       "venue": {"name": "Parque da Cidade", "address": "Estrada Interior", "district": "Porto",
                 "latitude": 41.17, "longitude": -8.678},
       "capacity": 20000, "queuePoints": ["Porta A", "Bar"],
       "confirmations": {"categories": ["CROWD_LEVEL"], "venueRadiusMeters": 500,
                         "highConfidenceAnswers": 8, "maxPromptsPerHour": 3}}
      """;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private EventManagementService service;
  @MockitoBean private JwtDecoder jwtDecoder;

  private final Event event =
      Event.restore(
          UUID.randomUUID(),
          UUID.randomUUID(),
          new EventProfile("São João no Parque", "Concerto gratuito."),
          new Admission(EventType.PUBLIC, new EventCode("SAOJOAO26")),
          new EventState(
              EventStatus.SCHEDULED,
              new Schedule(START, START.minusSeconds(3600), START.plusSeconds(5 * 3600)),
              new Venue("Parque da Cidade", "Estrada Interior", new Coordinates(41.17, -8.678))),
          new District("Porto"),
          new Capacity(20000),
          QueuePoints.named(List.of("Porta A", "Bar")),
          new ConfirmationSettings(
              Set.of("QUEUE_LENGTH", "CROWD_LEVEL"), new ConfirmationRules(500, 8, 3)));

  @Test
  void createsTheEventWithTheCodeOfItsType() throws Exception {
    when(service.register(any())).thenReturn(new ManagedEvent(event, 0));

    mockMvc
        .perform(asUser(post("/events"), CREATE))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/events/" + event.getId()))
        .andExpect(jsonPath("$.id").value(event.getId().toString()))
        .andExpect(jsonPath("$.type").value("PUBLIC"))
        .andExpect(jsonPath("$.status").value("SCHEDULED"))
        .andExpect(jsonPath("$.accessCode").value("SAOJOAO26"))
        .andExpect(jsonPath("$.ticketEventCode").doesNotExist())
        .andExpect(jsonPath("$.venue.district").value("Porto"))
        .andExpect(jsonPath("$.venue.latitude").value(41.17))
        .andExpect(jsonPath("$.capacity").value(20000))
        .andExpect(jsonPath("$.connectedUsers").value(0))
        .andExpect(jsonPath("$.queuePoints[1]").value("Bar"))
        .andExpect(jsonPath("$.confirmations.categories[0]").value("CROWD_LEVEL"))
        .andExpect(jsonPath("$.confirmations.categories[1]").value("QUEUE_LENGTH"))
        .andExpect(jsonPath("$.confirmations.maxPromptsPerHour").value(3));

    ArgumentCaptor<RegisterEventCommand> command =
        ArgumentCaptor.forClass(RegisterEventCommand.class);
    verify(service).register(command.capture());
    assertThat(command.getValue().userId()).isEqualTo(USER);
    assertThat(command.getValue().code()).isEqualTo("SAOJOAO26");
    assertThat(command.getValue().startDateTime()).isEqualTo(START);
    assertThat(command.getValue().venue().district()).isEqualTo("Porto");
    assertThat(command.getValue().queuePoints()).containsExactly("Porta A", "Bar");
    assertThat(command.getValue().confirmations().categories()).containsExactly("CROWD_LEVEL");
  }

  @Test
  void listsTheMembersEvents() throws Exception {
    when(service.listForMember(USER)).thenReturn(List.of(new ManagedEvent(event, 12)));

    mockMvc
        .perform(get("/organizations/me/events").with(jwt().jwt(t -> t.subject(USER.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("São João no Parque"))
        .andExpect(jsonPath("$[0].connectedUsers").value(12))
        .andExpect(jsonPath("$[0].startDateTime").value("2026-12-12T21:00:00Z"));
  }

  @Test
  void correctsTheFieldsSent() throws Exception {
    when(service.correct(any())).thenReturn(new ManagedEvent(event, 3));

    mockMvc
        .perform(
            asUser(
                patch("/events/{eventId}", event.getId()),
                "{\"name\": \"Novo\", \"accessCode\": \"SJ-2026\", \"capacity\": 100}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.connectedUsers").value(3));

    ArgumentCaptor<CorrectEventCommand> command =
        ArgumentCaptor.forClass(CorrectEventCommand.class);
    verify(service).correct(command.capture());
    assertThat(command.getValue())
        .isEqualTo(
            new CorrectEventCommand(USER, event.getId(), "Novo", null, 100, "SJ-2026", null, null));
  }

  @Test
  void answersRefusalsAsProblemsWithTheirCode() throws Exception {
    when(service.register(any()))
        .thenThrow(new ConflictException("EVENT_CODE_TAKEN", "Taken."))
        .thenThrow(new NotAllowedException("NOT_ORGANIZATION_MEMBER", "Members only."));

    mockMvc
        .perform(asUser(post("/events"), CREATE))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EVENT_CODE_TAKEN"));
    mockMvc
        .perform(asUser(post("/events"), CREATE))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_ORGANIZATION_MEMBER"));
  }

  @Test
  void refusesAnInvalidBodyOrNoToken() throws Exception {
    mockMvc.perform(asUser(post("/events"), "{}")).andExpect(status().isBadRequest());
    mockMvc
        .perform(
            asUser(
                patch("/events/{eventId}", event.getId()),
                "{\"queuePoints\": [\"" + "x".repeat(61) + "\"]}"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post("/events").contentType(MediaType.APPLICATION_JSON).content(CREATE).with(csrf()))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(service);
  }

  private static MockHttpServletRequestBuilder asUser(
      MockHttpServletRequestBuilder request, String body) {
    return request
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
        // MockMvc sends no bearer header, so the CSRF check applies; real API calls skip it.
        .with(csrf())
        .with(jwt().jwt(token -> token.subject(USER.toString())));
  }
}
