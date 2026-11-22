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

import com.crowded.core.event.api.mapper.CrowdLevelApiMapperImpl;
import com.crowded.core.event.api.mapper.FreshnessTestConfiguration;
import com.crowded.core.event.application.dto.PublishCrowdLevelCommand;
import com.crowded.core.event.application.service.CrowdLevelService;
import com.crowded.core.event.domain.model.CrowdLevel;
import com.crowded.core.event.domain.model.CrowdStatus;
import com.crowded.core.event.domain.model.Provenance;
import com.crowded.core.shared.domain.exception.ConflictException;
import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.shared.domain.exception.NotFoundException;
import com.crowded.core.shared.infrastructure.config.SecurityConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// The key location only has to resolve: the mocked decoder below replaces the one built from it.
@WebMvcTest(
    controllers = CrowdLevelController.class,
    properties = "JWT_PUBLIC_KEY_LOCATION=file:unused.pem")
@Import({
  SecurityConfiguration.class,
  CrowdLevelApiMapperImpl.class,
  FreshnessTestConfiguration.class,
  CrowdLevelControllerTest.FixedClock.class
})
class CrowdLevelControllerTest {

  private static final UUID EVENT = UUID.randomUUID();
  private static final UUID USER = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-11-20T21:00:00Z");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private CrowdLevelService service;
  @MockitoBean private JwtDecoder jwtDecoder;

  @Test
  void answersTheLevelWithItsSourceAndTime() throws Exception {
    when(service.publish(any()))
        .thenReturn(new CrowdStatus(CrowdLevel.HIGH, Provenance.organization("Ribeira Live", NOW)));

    mockMvc
        .perform(asUser(publish("{\"level\": \"HIGH\"}")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.level").value("HIGH"))
        .andExpect(jsonPath("$.provenance.sourceType").value("ORGANIZATION"))
        .andExpect(jsonPath("$.provenance.sourceName").value("Ribeira Live"))
        .andExpect(jsonPath("$.provenance.lastUpdatedAt").value(NOW.toString()))
        .andExpect(jsonPath("$.provenance.stale").value(false))
        .andExpect(jsonPath("$.provenance.staleAt").value(NOW.plusSeconds(20 * 60).toString()));

    verify(service).publish(new PublishCrowdLevelCommand(EVENT, USER, CrowdLevel.HIGH));
  }

  @Test
  void answersEachKindOfRefusalAsAProblemWithItsCode() throws Exception {
    when(service.publish(any()))
        .thenThrow(new NotAllowedException("NOT_EVENT_ORGANIZATION", "Not yours."))
        .thenThrow(new NotFoundException("EVENT_NOT_FOUND", "No event."))
        .thenThrow(new ConflictException("EVENT_CLOSED", "Closed."));

    mockMvc
        .perform(asUser(publish("{\"level\": \"LOW\"}")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_EVENT_ORGANIZATION"));
    mockMvc.perform(asUser(publish("{\"level\": \"LOW\"}"))).andExpect(status().isNotFound());
    mockMvc
        .perform(asUser(publish("{\"level\": \"LOW\"}")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EVENT_CLOSED"));
  }

  @Test
  void refusesAMissingOrUnknownLevel() throws Exception {
    mockMvc.perform(asUser(publish("{}"))).andExpect(status().isBadRequest());
    mockMvc.perform(asUser(publish("{\"level\": \"PACKED\"}"))).andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void refusesARequestWithoutAToken() throws Exception {
    mockMvc.perform(publish("{\"level\": \"LOW\"}")).andExpect(status().isUnauthorized());
    verifyNoInteractions(service);
  }

  private static MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder request) {
    return request.with(jwt().jwt(token -> token.subject(USER.toString())));
  }

  private static MockHttpServletRequestBuilder publish(String body) {
    return put("/events/{eventId}/crowd", EVENT)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
        // MockMvc sends no bearer header, so the CSRF check applies; real API calls skip it.
        .with(csrf());
  }

  @TestConfiguration
  static class FixedClock {

    @Bean
    Clock clock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }
}
