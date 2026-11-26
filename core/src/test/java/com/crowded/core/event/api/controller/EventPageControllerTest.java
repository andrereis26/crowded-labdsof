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

import com.crowded.core.connection.domain.model.ActiveAlert;
import com.crowded.core.event.api.mapper.EventPageApiMapperImpl;
import com.crowded.core.event.api.mapper.FreshnessTestConfiguration;
import com.crowded.core.event.application.dto.EventChangeView;
import com.crowded.core.event.application.dto.EventPage;
import com.crowded.core.event.application.dto.WeatherWarningView;
import com.crowded.core.event.application.service.EventViewService;
import com.crowded.core.event.application.service.LiveEventPages;
import com.crowded.core.event.domain.model.ChangeType;
import com.crowded.core.event.domain.model.CrowdLevel;
import com.crowded.core.event.domain.model.CrowdStatus;
import com.crowded.core.event.domain.model.EventStatus;
import com.crowded.core.event.domain.model.EventType;
import com.crowded.core.event.domain.model.Provenance;
import com.crowded.core.event.domain.model.QueuePoint;
import com.crowded.core.event.domain.model.QueueTime;
import com.crowded.core.event.domain.model.Severity;
import com.crowded.core.event.domain.model.WarningLevel;
import com.crowded.core.event.domain.model.WeatherForecast;
import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.shared.infrastructure.config.SecurityConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
  FreshnessTestConfiguration.class,
  EventPageControllerTest.FixedClock.class
})
class EventPageControllerTest {

  private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
  private static final UUID EVENT = UUID.randomUUID();
  private static final UUID USER = UUID.randomUUID();
  private static final UUID ORGANIZATION = UUID.randomUUID();
  private static final UUID ALERT = UUID.randomUUID();
  private static final UUID WARNING = UUID.randomUUID();
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
          POSTPONEMENT,
          new CrowdStatus(CrowdLevel.HIGH, Provenance.organization("Ribeira Live", NOW)),
          List.of(
              new QueuePoint(
                  "Porta A", new QueueTime(15, Provenance.organization("Ribeira Live", NOW)))),
          List.of(
              new ActiveAlert(
                  ALERT,
                  "ENTRANCE_CLOSURE",
                  Severity.CRITICAL,
                  "Usem a Porta B.",
                  NOW,
                  "Ribeira Live")),
          List.of(
              new WeatherForecast(
                  LocalDate.parse("2026-11-21"), "Chuvisco", 9.5, 14, 80, Provenance.ipma(NOW))),
          List.of(
              new WeatherWarningView(
                  WARNING,
                  "Vento",
                  WarningLevel.RED,
                  Severity.CRITICAL,
                  Instant.parse("2026-11-21T18:00:00Z"),
                  Instant.parse("2026-11-22T03:00:00Z"),
                  "Rajadas até 130 km/h.",
                  Provenance.ipma(NOW))),
          null);

  @Autowired private MockMvc mockMvc;

  @MockitoBean private EventViewService service;
  @MockitoBean private LiveEventPages live;
  @MockitoBean private JwtDecoder jwtDecoder;

  @Test
  void answersThePageWithItsVenueOrganizationLatestChangeAndCrowd() throws Exception {
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
        .andExpect(jsonPath("$.latestChange.provenance.lastUpdatedAt").value(NOW.toString()))
        .andExpect(jsonPath("$.crowd.level").value("HIGH"))
        .andExpect(jsonPath("$.crowd.provenance.sourceName").value("Ribeira Live"))
        .andExpect(jsonPath("$.crowd.provenance.lastUpdatedAt").value(NOW.toString()))
        .andExpect(jsonPath("$.crowd.provenance.stale").value(false))
        .andExpect(
            jsonPath("$.crowd.provenance.staleAt").value(NOW.plusSeconds(20 * 60).toString()))
        .andExpect(jsonPath("$.latestChange.provenance.stale").value(false))
        .andExpect(jsonPath("$.latestChange.provenance.staleAt").doesNotExist())
        .andExpect(jsonPath("$.queues[0].provenance.stale").value(false))
        .andExpect(jsonPath("$.queues[0].pointName").value("Porta A"))
        .andExpect(jsonPath("$.queues[0].waitMinutes").value(15))
        .andExpect(jsonPath("$.queues[0].provenance.sourceType").value("ORGANIZATION"))
        .andExpect(jsonPath("$.queues[0].provenance.lastUpdatedAt").value(NOW.toString()))
        .andExpect(jsonPath("$.activeAlerts[0].id").value(ALERT.toString()))
        .andExpect(jsonPath("$.activeAlerts[0].type").value("ENTRANCE_CLOSURE"))
        .andExpect(jsonPath("$.activeAlerts[0].severity").value("CRITICAL"))
        .andExpect(jsonPath("$.activeAlerts[0].message").value("Usem a Porta B."))
        .andExpect(jsonPath("$.activeAlerts[0].sentAt").value(NOW.toString()))
        .andExpect(jsonPath("$.activeAlerts[0].provenance.sourceType").value("ORGANIZATION"))
        .andExpect(jsonPath("$.activeAlerts[0].provenance.sourceName").value("Ribeira Live"))
        .andExpect(jsonPath("$.activeAlerts[0].provenance.lastUpdatedAt").value(NOW.toString()))
        .andExpect(jsonPath("$.activeAlerts[0].provenance.stale").value(false))
        .andExpect(jsonPath("$.activeAlerts[0].provenance.staleAt").doesNotExist())
        .andExpect(jsonPath("$.weather.forecast[0].date").value("2026-11-21"))
        .andExpect(jsonPath("$.weather.forecast[0].conditions").value("Chuvisco"))
        .andExpect(jsonPath("$.weather.forecast[0].minTemperature").value(9.5))
        .andExpect(jsonPath("$.weather.forecast[0].precipitationProbability").value(80.0))
        .andExpect(
            jsonPath("$.weather.forecast[0].provenance.sourceType").value("EXTERNAL_SERVICE"))
        .andExpect(jsonPath("$.weather.forecast[0].provenance.sourceName").value("IPMA"))
        .andExpect(jsonPath("$.weather.forecast[0].provenance.lastUpdatedAt").value(NOW.toString()))
        .andExpect(jsonPath("$.weather.warnings[0].id").value(WARNING.toString()))
        .andExpect(jsonPath("$.weather.warnings[0].awarenessType").value("Vento"))
        .andExpect(jsonPath("$.weather.warnings[0].level").value("RED"))
        .andExpect(jsonPath("$.weather.warnings[0].severity").value("CRITICAL"))
        .andExpect(jsonPath("$.weather.warnings[0].startTime").value("2026-11-21T18:00:00Z"))
        .andExpect(jsonPath("$.weather.warnings[0].endTime").value("2026-11-22T03:00:00Z"))
        .andExpect(jsonPath("$.weather.warnings[0].description").value("Rajadas até 130 km/h."))
        .andExpect(jsonPath("$.weather.warnings[0].provenance.sourceName").value("IPMA"))
        .andExpect(jsonPath("$.weather.forecast[0].provenance.stale").value(false))
        .andExpect(jsonPath("$.weather.warnings[0].provenance.stale").value(false))
        .andExpect(jsonPath("$.weather.warnings[0].provenance.staleAt").doesNotExist());
  }

  @Test
  void flagsTheCrowdLevelAndTheQueuesOnceTheirPeriodHasEnded() throws Exception {
    when(service.page(USER, EVENT))
        .thenReturn(pageUpdatedAt(NOW.minusSeconds(20 * 60 - 1)))
        .thenReturn(pageUpdatedAt(NOW.minusSeconds(20 * 60)));

    mockMvc
        .perform(asUser(get("/events/{eventId}", EVENT)))
        .andExpect(jsonPath("$.crowd.provenance.stale").value(false))
        .andExpect(jsonPath("$.queues[0].provenance.stale").value(false));
    mockMvc
        .perform(asUser(get("/events/{eventId}", EVENT)))
        .andExpect(jsonPath("$.crowd.provenance.stale").value(true))
        .andExpect(jsonPath("$.crowd.provenance.staleAt").value(NOW.toString()))
        .andExpect(jsonPath("$.queues[0].provenance.stale").value(true))
        .andExpect(jsonPath("$.queues[0].provenance.staleAt").value(NOW.toString()));
  }

  @Test
  void flagsTheForecastAfterThreeHoursButNeverAWarning() throws Exception {
    when(service.page(USER, EVENT))
        .thenReturn(weatherSeenAt(NOW.minusSeconds(3 * 3600 - 1)))
        .thenReturn(weatherSeenAt(NOW.minusSeconds(3 * 3600)));

    mockMvc
        .perform(asUser(get("/events/{eventId}", EVENT)))
        .andExpect(jsonPath("$.weather.forecast[0].provenance.stale").value(false))
        .andExpect(jsonPath("$.weather.warnings[0].provenance.stale").value(false));
    mockMvc
        .perform(asUser(get("/events/{eventId}", EVENT)))
        .andExpect(jsonPath("$.weather.forecast[0].provenance.stale").value(true))
        .andExpect(jsonPath("$.weather.forecast[0].provenance.staleAt").value(NOW.toString()))
        .andExpect(jsonPath("$.weather.warnings[0].provenance.stale").value(false));
  }

  private static EventPage weatherSeenAt(Instant at) {
    WeatherWarningView warning = PAGE.warnings().getFirst();
    return new EventPage(
        PAGE.id(),
        PAGE.name(),
        PAGE.description(),
        PAGE.type(),
        PAGE.status(),
        PAGE.startDateTime(),
        PAGE.doorsOpenAt(),
        PAGE.endDateTime(),
        PAGE.venueName(),
        PAGE.address(),
        PAGE.district(),
        PAGE.latitude(),
        PAGE.longitude(),
        PAGE.organizationId(),
        PAGE.organizationName(),
        PAGE.latestChange(),
        PAGE.crowd(),
        PAGE.queues(),
        PAGE.activeAlerts(),
        List.of(
            new WeatherForecast(
                LocalDate.parse("2026-11-21"), "Chuvisco", 9.5, 14, 80, Provenance.ipma(at))),
        List.of(
            new WeatherWarningView(
                warning.id(),
                warning.awarenessType(),
                warning.level(),
                warning.severity(),
                warning.startTime(),
                warning.endTime(),
                warning.description(),
                Provenance.ipma(at))),
        null);
  }

  private static EventPage pageUpdatedAt(Instant at) {
    Provenance ribeira = Provenance.organization("Ribeira Live", at);
    return new EventPage(
        PAGE.id(),
        PAGE.name(),
        PAGE.description(),
        PAGE.type(),
        PAGE.status(),
        PAGE.startDateTime(),
        PAGE.doorsOpenAt(),
        PAGE.endDateTime(),
        PAGE.venueName(),
        PAGE.address(),
        PAGE.district(),
        PAGE.latitude(),
        PAGE.longitude(),
        PAGE.organizationId(),
        PAGE.organizationName(),
        PAGE.latestChange(),
        new CrowdStatus(CrowdLevel.HIGH, ribeira),
        List.of(new QueuePoint("Porta A", new QueueTime(15, ribeira))),
        PAGE.activeAlerts(),
        PAGE.forecast(),
        PAGE.warnings(),
        null);
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
