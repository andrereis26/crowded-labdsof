package com.crowded.core.connection.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crowded.core.connection.api.mapper.ConnectionApiMapperImpl;
import com.crowded.core.connection.application.dto.JoinWithAccessCodeCommand;
import com.crowded.core.connection.application.dto.JoinedEvent;
import com.crowded.core.connection.application.dto.MyConnection;
import com.crowded.core.connection.application.dto.MyConnections;
import com.crowded.core.connection.application.dto.MyEvent;
import com.crowded.core.connection.application.dto.TicketSummary;
import com.crowded.core.connection.application.service.AccessCodeConnectionService;
import com.crowded.core.connection.application.service.MyConnectionService;
import com.crowded.core.connection.domain.model.ActiveAlert;
import com.crowded.core.connection.domain.model.ConnectionType;
import com.crowded.core.event.api.mapper.EventPageApiMapperImpl;
import com.crowded.core.event.application.dto.EventChangeView;
import com.crowded.core.event.domain.model.ChangeType;
import com.crowded.core.event.domain.model.EventStatus;
import com.crowded.core.event.domain.model.Provenance;
import com.crowded.core.event.domain.model.Severity;
import com.crowded.core.shared.domain.exception.NotFoundException;
import com.crowded.core.shared.infrastructure.config.SecurityConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(
    controllers = ConnectionController.class,
    properties = "JWT_PUBLIC_KEY_LOCATION=file:unused.pem")
@Import({SecurityConfiguration.class, ConnectionApiMapperImpl.class, EventPageApiMapperImpl.class})
class ConnectionControllerTest {

  private static final UUID USER = UUID.randomUUID();
  private static final UUID EVENT = UUID.randomUUID();
  private static final UUID CONNECTION = UUID.randomUUID();
  private static final Instant START = Instant.parse("2026-11-20T20:00:00Z");

  @Autowired private MockMvc mockMvc;
  @MockitoBean private AccessCodeConnectionService service;
  @MockitoBean private MyConnectionService myConnections;
  @MockitoBean private JwtDecoder jwtDecoder;

  @Test
  void answersANewConnectionWithItsEvent() throws Exception {
    when(service.joinWithAccessCode(new JoinWithAccessCodeCommand(USER, "saojoao26")))
        .thenReturn(joined(true));

    mockMvc
        .perform(asUser(join("{\"accessCode\":\"saojoao26\"}")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.connectionId").value(CONNECTION.toString()))
        .andExpect(jsonPath("$.event.id").value(EVENT.toString()))
        .andExpect(jsonPath("$.event.name").value("Arraial de São João"))
        .andExpect(jsonPath("$.event.startDateTime").value(START.toString()))
        .andExpect(jsonPath("$.event.doorsOpenAt").isEmpty())
        .andExpect(jsonPath("$.event.venueName").value("Fontainhas"));
  }

  @Test
  void answersTheExistingConnectionWithoutCreatingOne() throws Exception {
    when(service.joinWithAccessCode(any())).thenReturn(joined(false));

    mockMvc
        .perform(asUser(join("{\"accessCode\":\"SAOJOAO26\"}")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.connectionId").value(CONNECTION.toString()));
  }

  @Test
  void answersACodeOfNoEventAsAProblem() throws Exception {
    when(service.joinWithAccessCode(any()))
        .thenThrow(new NotFoundException("ACCESS_CODE_NOT_FOUND", "No event."));

    mockMvc
        .perform(asUser(join("{\"accessCode\":\"NOPE2026\"}")))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("ACCESS_CODE_NOT_FOUND"));
  }

  @Test
  void refusesARequestWithoutACode() throws Exception {
    mockMvc.perform(asUser(join("{\"accessCode\":\" \"}"))).andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void refusesARequestWithoutAToken() throws Exception {
    mockMvc.perform(join("{\"accessCode\":\"SAOJOAO26\"}")).andExpect(status().isUnauthorized());
    verifyNoInteractions(service);
  }

  @Test
  void listsTheSignedInParticipantsEventsWithTheirChangeAlertsAndTicket() throws Exception {
    EventChangeView change =
        new EventChangeView(
            UUID.randomUUID(),
            ChangeType.CANCELLATION,
            Severity.CRITICAL,
            Map.of("status", "SCHEDULED"),
            Map.of("status", "CANCELLED"),
            false,
            null,
            "Cancelado.",
            Provenance.organization("Arraiais", START));
    ActiveAlert alert =
        new ActiveAlert(
            UUID.randomUUID(),
            "ENTRANCE_CLOSURE",
            Severity.WARNING,
            "Entrada fechada.",
            START,
            "Ribeira Live");
    when(myConnections.listOf(USER))
        .thenReturn(
            new MyConnections(
                List.of(
                    new MyConnection(
                        CONNECTION,
                        ConnectionType.TICKET,
                        new MyEvent(
                            EVENT,
                            "Arraial",
                            EventStatus.CANCELLED,
                            START,
                            START.minusSeconds(3600),
                            "Fontainhas",
                            change,
                            List.of(alert)),
                        new TicketSummary("A", null, "14", true))),
                START));

    mockMvc
        .perform(asUser(get("/me/connections")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.syncedAt").value(START.toString()))
        .andExpect(jsonPath("$.connections[0].connectionId").value(CONNECTION.toString()))
        .andExpect(jsonPath("$.connections[0].type").value("TICKET"))
        .andExpect(jsonPath("$.connections[0].event.id").value(EVENT.toString()))
        .andExpect(jsonPath("$.connections[0].event.status").value("CANCELLED"))
        .andExpect(jsonPath("$.connections[0].event.venueName").value("Fontainhas"))
        .andExpect(jsonPath("$.connections[0].event.latestChange.type").value("CANCELLATION"))
        .andExpect(jsonPath("$.connections[0].event.latestChange.severity").value("CRITICAL"))
        .andExpect(
            jsonPath("$.connections[0].event.activeAlerts[0].id").value(alert.alertId().toString()))
        .andExpect(jsonPath("$.connections[0].event.activeAlerts[0].severity").value("WARNING"))
        .andExpect(jsonPath("$.connections[0].ticket.gate").value("A"))
        .andExpect(jsonPath("$.connections[0].ticket.seat").value("14"))
        .andExpect(jsonPath("$.connections[0].ticket.hasEntryCode").value(true));
  }

  @Test
  void answersAnEmptyListWhenThereAreNoConnections() throws Exception {
    when(myConnections.listOf(USER)).thenReturn(new MyConnections(List.of(), START));

    mockMvc
        .perform(asUser(get("/me/connections")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.connections").isEmpty());
  }

  @Test
  void refusesTheListWithoutAToken() throws Exception {
    mockMvc.perform(get("/me/connections")).andExpect(status().isUnauthorized());
    verifyNoInteractions(myConnections);
  }

  private static JoinedEvent joined(boolean created) {
    return new JoinedEvent(
        CONNECTION, created, EVENT, "Arraial de São João", START, null, "Fontainhas");
  }

  private static MockHttpServletRequestBuilder join(String body) {
    return post("/connections").contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private static MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder request) {
    return request.with(jwt().jwt(token -> token.subject(USER.toString())));
  }
}
