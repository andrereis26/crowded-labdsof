package com.crowded.core.event.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crowded.core.event.api.mapper.EventPageApiMapperImpl;
import com.crowded.core.event.application.dto.EventChangeView;
import com.crowded.core.event.application.dto.EventPage;
import com.crowded.core.event.application.service.EventViewService;
import com.crowded.core.event.application.service.LiveEventPages;
import com.crowded.core.event.domain.model.ChangeType;
import com.crowded.core.event.domain.model.EventStatus;
import com.crowded.core.event.domain.model.EventType;
import com.crowded.core.event.domain.model.Provenance;
import com.crowded.core.event.domain.model.Severity;
import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.shared.infrastructure.config.SecurityConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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

@WebMvcTest(
    controllers = EventPageController.class,
    properties = "JWT_PUBLIC_KEY_LOCATION=file:unused.pem")
@Import({
  SecurityConfiguration.class,
  EventPageApiMapperImpl.class,
  EventPageControllerTest.FixedClock.class
})
class EventPageControllerTest {

  private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
  private static final UUID EVENT = UUID.randomUUID();
  private static final UUID USER = UUID.randomUUID();
  private static final UUID ORGANIZATION = UUID.randomUUID();
  private static final EventChangeView POSTPONEMENT =
      new EventChangeView(
          UUID.randomUUID(),
          ChangeType.POSTPONEMENT,
          Severity.WARNING,
          Map.of("startDateTime", "2026-11-20T20:00:00Z"),
          Map.of("startDateTime", "2026-11-21T20:00:00Z"),
          true,
          null,
          "Por causa do vento.",
          Provenance.organization("Ribeira Live", NOW));
  private static final EventPage PAGE =
      new EventPage(
          EVENT,
          "Festival Margens",
          "Música no parque.",
          EventType.TICKETED,
          EventStatus.POSTPONED,
          Instant.parse("2026-11-21T20:00:00Z"),
          null,
          null,
          "Parque da Cidade",
          "Estrada Interior da Circunvalação",
          "Porto",
          41.17,
          -8.68,
          ORGANIZATION,
          "Ribeira Live",
          POSTPONEMENT);

  @Autowired private MockMvc mockMvc;

  @MockitoBean private EventViewService service;
  @MockitoBean private LiveEventPages live;
  @MockitoBean private JwtDecoder jwtDecoder;

  @Test
  void answersThePageWithItsVenueOrganizationAndLatestChange() throws Exception {
    when(service.page(USER, EVENT)).thenReturn(PAGE);

    mockMvc
        .perform(asUser(get("/events/{eventId}", EVENT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(EVENT.toString()))
        .andExpect(jsonPath("$.type").value("TICKETED"))
        .andExpect(jsonPath("$.status").value("POSTPONED"))
        .andExpect(jsonPath("$.startDateTime").value("2026-11-21T20:00:00Z"))
        .andExpect(jsonPath("$.venue.name").value("Parque da Cidade"))
        .andExpect(jsonPath("$.venue.district").value("Porto"))
        .andExpect(jsonPath("$.venue.latitude").value(41.17))
        .andExpect(jsonPath("$.organization.name").value("Ribeira Live"))
        .andExpect(jsonPath("$.latestChange.type").value("POSTPONEMENT"))
        .andExpect(jsonPath("$.latestChange.newValue.startDateTime").value("2026-11-21T20:00:00Z"))
        .andExpect(jsonPath("$.latestChange.provenance.sourceType").value("ORGANIZATION"))
        .andExpect(jsonPath("$.latestChange.provenance.lastUpdatedAt").value(NOW.toString()));
  }

  @Test
  void answersTheHistoryAsAListThatMayBeEmpty() throws Exception {
    when(service.history(USER, EVENT)).thenReturn(List.of(POSTPONEMENT)).thenReturn(List.of());

    mockMvc
        .perform(asUser(get("/events/{eventId}/changes", EVENT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(POSTPONEMENT.id().toString()))
        .andExpect(jsonPath("$[0].severity").value("WARNING"))
        .andExpect(jsonPath("$[0].ticketStillValid").value(true))
        .andExpect(jsonPath("$[0].message").value("Por causa do vento."))
        .andExpect(jsonPath("$[0].provenance.sourceName").value("Ribeira Live"));
    mockMvc
        .perform(asUser(get("/events/{eventId}/changes", EVENT)))
        .andExpect(status().isOk())
        .andExpect(content().json("[]"));
  }

  @Test
  void refusesAUserWhoMayNotSeeTheEventAsAProblem() throws Exception {
    when(service.page(USER, EVENT))
        .thenThrow(new NotAllowedException("NOT_CONNECTED", "Not connected."));

    mockMvc
        .perform(asUser(get("/events/{eventId}", EVENT)))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("NOT_CONNECTED"));
  }

  @Test
  void refusesARequestWithoutAToken() throws Exception {
    mockMvc.perform(get("/events/{eventId}/changes", EVENT)).andExpect(status().isUnauthorized());
  }

  @Test
  void opensTheStreamWithTheCurrentPageAndSubscribesToItsChanges() throws Exception {
    when(service.page(USER, EVENT)).thenReturn(PAGE);

    mockMvc
        .perform(asUser(get("/events/{eventId}/live", EVENT)))
        .andExpect(request().asyncStarted())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("event:event")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Festival Margens")));
    verify(live).subscribe(eq(EVENT), any());
  }

  private static MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder request) {
    return request.with(jwt().jwt(token -> token.subject(USER.toString())));
  }

  @TestConfiguration
  static class FixedClock {

    @Bean
    Clock clock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }
}
