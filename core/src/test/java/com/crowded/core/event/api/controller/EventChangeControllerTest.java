package com.crowded.core.event.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crowded.core.event.api.mapper.EventChangeApiMapperImpl;
import com.crowded.core.event.application.dto.PublishChangeCommand;
import com.crowded.core.event.application.dto.PublishedChange;
import com.crowded.core.event.application.service.EventChangeService;
import com.crowded.core.event.domain.model.ChangeType;
import com.crowded.core.event.domain.model.Severity;
import com.crowded.core.shared.domain.exception.ConflictException;
import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.shared.domain.exception.NotFoundException;
import com.crowded.core.shared.domain.exception.RuleViolationException;
import com.crowded.core.shared.infrastructure.config.SecurityConfiguration;
import java.time.Instant;
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
    controllers = EventChangeController.class,
    properties = "JWT_PUBLIC_KEY_LOCATION=file:unused.pem")
@Import({SecurityConfiguration.class, EventChangeApiMapperImpl.class})
class EventChangeControllerTest {

  private static final UUID EVENT = UUID.randomUUID();
  private static final UUID USER = UUID.randomUUID();
  private static final String CANCELLATION = "{\"type\": \"CANCELLATION\"}";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private EventChangeService service;
  @MockitoBean private JwtDecoder jwtDecoder;

  @Test
  void answersCreatedWithTheNewChange() throws Exception {
    UUID change = UUID.randomUUID();
    when(service.publish(any())).thenReturn(new PublishedChange(change, Severity.CRITICAL, true));

    mockMvc
        .perform(
            asUser(
                publish(
                    """
                    {"type": "POSTPONEMENT",
                     "newValue": {"startDateTime": "2026-12-04T20:00:00+00:00"},
                     "ticketStillValid": true, "refundInfo": "No site", "message": "Lamentamos."}
                    """)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.changeId").value(change.toString()))
        .andExpect(jsonPath("$.severity").value("CRITICAL"));

    ArgumentCaptor<PublishChangeCommand> command =
        ArgumentCaptor.forClass(PublishChangeCommand.class);
    verify(service).publish(command.capture());
    assertThat(command.getValue().eventId()).isEqualTo(EVENT);
    assertThat(command.getValue().userId()).isEqualTo(USER);
    assertThat(command.getValue().change().type()).isEqualTo(ChangeType.POSTPONEMENT);
    assertThat(command.getValue().change().newValues().startDateTime())
        .isEqualTo(Instant.parse("2026-12-04T20:00:00Z"));
    assertThat(command.getValue().change().ticketStillValid()).isTrue();
    assertThat(command.getValue().change().refundInfo()).isEqualTo("No site");
    assertThat(command.getValue().change().message()).isEqualTo("Lamentamos.");
  }

  @Test
  void answersOkWithTheFirstChangeWhenItIsSentAgain() throws Exception {
    UUID change = UUID.randomUUID();
    when(service.publish(any())).thenReturn(new PublishedChange(change, Severity.WARNING, false));

    mockMvc
        .perform(asUser(publish(CANCELLATION)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.changeId").value(change.toString()));
  }

  @Test
  void answersEachKindOfRefusalAsAProblemWithItsCode() throws Exception {
    when(service.publish(any()))
        .thenThrow(new NotAllowedException("NOT_EVENT_ORGANIZATION", "Not yours."))
        .thenThrow(new NotFoundException("EVENT_NOT_FOUND", "No event."))
        .thenThrow(new ConflictException("EVENT_CLOSED", "Closed."))
        .thenThrow(new RuleViolationException("START_IN_PAST", "Past."));

    mockMvc
        .perform(asUser(publish(CANCELLATION)))
        .andExpect(status().isForbidden())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("NOT_EVENT_ORGANIZATION"))
        .andExpect(jsonPath("$.detail").value("Not yours."));
    mockMvc.perform(asUser(publish(CANCELLATION))).andExpect(status().isNotFound());
    mockMvc.perform(asUser(publish(CANCELLATION))).andExpect(status().isConflict());
    mockMvc
        .perform(asUser(publish(CANCELLATION)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("START_IN_PAST"));
  }

  @Test
  void refusesATokenWithoutAUserId() throws Exception {
    mockMvc
        .perform(publish(CANCELLATION).with(jwt().jwt(token -> token.subject("service"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_EVENT_ORGANIZATION"));
    mockMvc
        .perform(publish(CANCELLATION).with(jwt().jwt(token -> token.claims(c -> c.remove("sub")))))
        .andExpect(status().isForbidden());
    verifyNoInteractions(service);
  }

  @Test
  void refusesAnInvalidBody() throws Exception {
    mockMvc.perform(asUser(publish("{}"))).andExpect(status().isBadRequest());
    mockMvc
        .perform(asUser(publish("{\"type\": \"VENUE_CHANGE\", \"newValue\": {\"latitude\": 91}}")))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void refusesARequestWithoutAToken() throws Exception {
    mockMvc.perform(publish(CANCELLATION)).andExpect(status().isUnauthorized());
  }

  private static MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder request) {
    return request.with(jwt().jwt(token -> token.subject(USER.toString())));
  }

  private static MockHttpServletRequestBuilder publish(String body) {
    return post("/events/{eventId}/changes", EVENT)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
        // MockMvc sends no bearer header, so the CSRF check applies; real API calls skip it.
        .with(csrf());
  }
}
