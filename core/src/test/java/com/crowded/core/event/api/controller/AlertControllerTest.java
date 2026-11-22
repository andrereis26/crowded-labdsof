package com.crowded.core.event.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crowded.core.event.api.mapper.AlertApiMapperImpl;
import com.crowded.core.event.application.dto.SendAlertCommand;
import com.crowded.core.event.application.service.AlertService;
import com.crowded.core.event.domain.model.AlertType;
import com.crowded.core.event.domain.model.EventState;
import com.crowded.core.event.domain.model.EventStatus;
import com.crowded.core.event.domain.model.Schedule;
import com.crowded.core.event.domain.model.StoredEvents;
import com.crowded.core.event.domain.model.Venue;
import com.crowded.core.shared.domain.exception.ConflictException;
import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.shared.domain.exception.NotFoundException;
import com.crowded.core.shared.infrastructure.config.SecurityConfiguration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    controllers = AlertController.class,
    properties = "JWT_PUBLIC_KEY_LOCATION=file:unused.pem")
@Import({SecurityConfiguration.class, AlertApiMapperImpl.class})
class AlertControllerTest {

  private static final UUID EVENT = UUID.randomUUID();
  private static final UUID USER = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-11-20T21:00:00Z");
  private static final String CLOSURE =
      "{\"type\": \"ENTRANCE_CLOSURE\", \"message\": \"Porta A fechada.\"}";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AlertService service;
  @MockitoBean private JwtDecoder jwtDecoder;

  @Test
  void answersTheSentAlertWithItsSeverityAndRecipients() throws Exception {
    when(service.send(any()))
        .thenReturn(
            StoredEvents.restore(
                    EVENT,
                    UUID.randomUUID(),
                    new EventState(
                        EventStatus.ONGOING,
                        new Schedule(NOW.minusSeconds(3600), null, NOW.plusSeconds(3600)),
                        new Venue("Coliseu", "Porto", null)))
                .sendAlert(AlertType.ENTRANCE_CLOSURE, "Porta A fechada.", 120, NOW));

    mockMvc
        .perform(asUser(send(CLOSURE)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.type").value("ENTRANCE_CLOSURE"))
        .andExpect(jsonPath("$.severity").value("CRITICAL"))
        .andExpect(jsonPath("$.message").value("Porta A fechada."))
        .andExpect(jsonPath("$.sentAt").value(NOW.toString()))
        .andExpect(jsonPath("$.recipientsCount").value(120));

    verify(service)
        .send(new SendAlertCommand(EVENT, USER, AlertType.ENTRANCE_CLOSURE, "Porta A fechada."));
  }

  @Test
  void answersEachKindOfRefusalAsAProblemWithItsCode() throws Exception {
    when(service.send(any()))
        .thenThrow(new NotAllowedException("NOT_EVENT_ORGANIZATION", "Not yours."))
        .thenThrow(new NotFoundException("EVENT_NOT_FOUND", "No event."))
        .thenThrow(new ConflictException("EVENT_CLOSED", "Closed."));

    mockMvc
        .perform(asUser(send(CLOSURE)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_EVENT_ORGANIZATION"));
    mockMvc
        .perform(asUser(send(CLOSURE)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    mockMvc
        .perform(asUser(send(CLOSURE)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EVENT_CLOSED"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"message\": \"Porta A fechada.\"}",
        "{\"type\": \"WEATHER_WARNING\", \"message\": \"Chuva.\"}",
        "{\"type\": \"OTHER\"}",
        "{\"type\": \"OTHER\", \"message\": \"   \"}",
        "{}"
      })
  void refusesAMissingOrUnknownTypeAndAMissingMessage(String body) throws Exception {
    mockMvc.perform(asUser(send(body))).andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void refusesAMessageLongerThanTheLimit() throws Exception {
    String body = "{\"type\": \"OTHER\", \"message\": \"" + "a".repeat(501) + "\"}";

    mockMvc.perform(asUser(send(body))).andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void refusesARequestWithoutAToken() throws Exception {
    mockMvc.perform(send(CLOSURE)).andExpect(status().isUnauthorized());
    verifyNoInteractions(service);
  }

  private static MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder request) {
    return request.with(jwt().jwt(token -> token.subject(USER.toString())));
  }

  private static MockHttpServletRequestBuilder send(String body) {
    return post("/events/{eventId}/alerts", EVENT)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
        // MockMvc sends no bearer header, so the CSRF check applies; real API calls skip it.
        .with(csrf());
  }
}
