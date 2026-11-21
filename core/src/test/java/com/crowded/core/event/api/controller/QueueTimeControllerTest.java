package com.crowded.core.event.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crowded.core.event.api.mapper.EventPageApiMapperImpl;
import com.crowded.core.event.api.mapper.QueueTimeApiMapperImpl;
import com.crowded.core.event.application.dto.PublishQueueTimesCommand;
import com.crowded.core.event.application.service.QueueTimeService;
import com.crowded.core.event.domain.model.Provenance;
import com.crowded.core.event.domain.model.QueuePoint;
import com.crowded.core.event.domain.model.QueueTime;
import com.crowded.core.event.domain.model.QueueWait;
import com.crowded.core.shared.domain.exception.ConflictException;
import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.shared.domain.exception.NotFoundException;
import com.crowded.core.shared.domain.exception.RuleViolationException;
import com.crowded.core.shared.infrastructure.config.SecurityConfiguration;
import java.time.Instant;
import java.util.List;
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
    controllers = QueueTimeController.class,
    properties = "JWT_PUBLIC_KEY_LOCATION=file:unused.pem")
@Import({SecurityConfiguration.class, QueueTimeApiMapperImpl.class, EventPageApiMapperImpl.class})
class QueueTimeControllerTest {

  private static final UUID EVENT = UUID.randomUUID();
  private static final UUID USER = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-11-20T21:00:00Z");
  private static final String BAR_15 =
      "{\"queues\": [{\"pointName\": \"Bar\", \"waitMinutes\": 15}]}";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private QueueTimeService service;
  @MockitoBean private JwtDecoder jwtDecoder;

  @Test
  void answersEveryTimedPointWithItsSourceAndTime() throws Exception {
    Provenance ribeira = Provenance.organization("Ribeira Live", NOW);
    when(service.publish(any()))
        .thenReturn(
            List.of(
                new QueuePoint("Porta A", new QueueTime(5, ribeira)),
                new QueuePoint("Bar", new QueueTime(15, ribeira))));

    mockMvc
        .perform(asUser(publish(BAR_15)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.queues.length()").value(2))
        .andExpect(jsonPath("$.queues[1].pointName").value("Bar"))
        .andExpect(jsonPath("$.queues[1].waitMinutes").value(15))
        .andExpect(jsonPath("$.queues[1].provenance.sourceType").value("ORGANIZATION"))
        .andExpect(jsonPath("$.queues[1].provenance.sourceName").value("Ribeira Live"))
        .andExpect(jsonPath("$.queues[1].provenance.lastUpdatedAt").value(NOW.toString()));

    verify(service)
        .publish(new PublishQueueTimesCommand(EVENT, USER, List.of(new QueueWait("Bar", 15))));
  }

  @Test
  void answersEachKindOfRefusalAsAProblemWithItsCode() throws Exception {
    when(service.publish(any()))
        .thenThrow(new NotAllowedException("NOT_EVENT_ORGANIZATION", "Not yours."))
        .thenThrow(new NotFoundException("EVENT_NOT_FOUND", "No event."))
        .thenThrow(new ConflictException("EVENT_CLOSED", "Closed."))
        .thenThrow(new RuleViolationException("QUEUE_POINT_UNKNOWN", "No such point."));

    mockMvc
        .perform(asUser(publish(BAR_15)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_EVENT_ORGANIZATION"));
    mockMvc.perform(asUser(publish(BAR_15))).andExpect(status().isNotFound());
    mockMvc
        .perform(asUser(publish(BAR_15)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EVENT_CLOSED"));
    mockMvc
        .perform(asUser(publish(BAR_15)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("QUEUE_POINT_UNKNOWN"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"queues\": [{\"pointName\": \"Bar\", \"waitMinutes\": -5}]}",
        "{\"queues\": [{\"pointName\": \"Bar\", \"waitMinutes\": \"dez\"}]}",
        "{\"queues\": [{\"pointName\": \"Bar\"}]}",
        "{\"queues\": [{\"pointName\": \" \", \"waitMinutes\": 5}]}",
        "{\"queues\": []}",
        "{}"
      })
  void refusesANegativeNonNumericOrMissingTime(String body) throws Exception {
    mockMvc.perform(asUser(publish(body))).andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void refusesARequestWithoutAToken() throws Exception {
    mockMvc.perform(publish(BAR_15)).andExpect(status().isUnauthorized());
    verifyNoInteractions(service);
  }

  private static MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder request) {
    return request.with(jwt().jwt(token -> token.subject(USER.toString())));
  }

  private static MockHttpServletRequestBuilder publish(String body) {
    return put("/events/{eventId}/queues", EVENT)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
        // MockMvc sends no bearer header, so the CSRF check applies; real API calls skip it.
        .with(csrf());
  }
}
