package com.crowded.core.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crowded.core.SecurityConfiguration;
import com.crowded.core.web.ApiException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// The key location only has to resolve: the mocked decoder below replaces the one built from it.
@WebMvcTest(
    controllers = EventChangeController.class,
    properties = "JWT_PUBLIC_KEY_LOCATION=file:unused.pem")
@Import(SecurityConfiguration.class)
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
    when(service.publish(eq(EVENT), eq(USER), any()))
        .thenReturn(new PublishedChange(change, Severity.CRITICAL, true));

    mockMvc
        .perform(publish(CANCELLATION).with(jwt().jwt(token -> token.subject(USER.toString()))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.changeId").value(change.toString()))
        .andExpect(jsonPath("$.severity").value("CRITICAL"));
  }

  @Test
  void answersOkWithTheFirstChangeWhenItIsSentAgain() throws Exception {
    UUID change = UUID.randomUUID();
    when(service.publish(eq(EVENT), eq(USER), any()))
        .thenReturn(new PublishedChange(change, Severity.WARNING, false));

    mockMvc
        .perform(publish(CANCELLATION).with(jwt().jwt(token -> token.subject(USER.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.changeId").value(change.toString()));
  }

  @Test
  void answersARefusalAsAProblemWithItsCode() throws Exception {
    when(service.publish(eq(EVENT), eq(USER), any()))
        .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "NOT_EVENT_ORGANIZATION", "Not yours."));

    mockMvc
        .perform(publish(CANCELLATION).with(jwt().jwt(token -> token.subject(USER.toString()))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_EVENT_ORGANIZATION"))
        .andExpect(jsonPath("$.detail").value("Not yours."));
  }

  @Test
  void refusesATokenWithoutAUserId() throws Exception {
    mockMvc
        .perform(publish(CANCELLATION).with(jwt().jwt(token -> token.subject("service"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_EVENT_ORGANIZATION"));
    verifyNoInteractions(service);
  }

  @Test
  void refusesAnInvalidBody() throws Exception {
    mockMvc
        .perform(publish("{}").with(jwt().jwt(token -> token.subject(USER.toString()))))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            publish("{\"type\": \"VENUE_CHANGE\", \"newValue\": {\"latitude\": 91}}")
                .with(jwt().jwt(token -> token.subject(USER.toString()))))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void refusesARequestWithoutAToken() throws Exception {
    mockMvc.perform(publish(CANCELLATION)).andExpect(status().isUnauthorized());
  }

  private static MockHttpServletRequestBuilder publish(String body) {
    return post("/events/{eventId}/changes", EVENT)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
        // MockMvc sends no bearer header, so the CSRF check applies; real API calls skip it.
        .with(csrf());
  }
}
