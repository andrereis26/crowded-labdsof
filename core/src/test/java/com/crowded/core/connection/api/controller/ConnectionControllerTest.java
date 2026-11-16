package com.crowded.core.connection.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crowded.core.connection.api.mapper.ConnectionApiMapperImpl;
import com.crowded.core.connection.application.dto.JoinWithAccessCodeCommand;
import com.crowded.core.connection.application.dto.JoinedEvent;
import com.crowded.core.connection.application.service.AccessCodeConnectionService;
import com.crowded.core.shared.domain.exception.NotFoundException;
import com.crowded.core.shared.infrastructure.config.SecurityConfiguration;
import java.time.Instant;
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
@Import({SecurityConfiguration.class, ConnectionApiMapperImpl.class})
class ConnectionControllerTest {

  private static final UUID USER = UUID.randomUUID();
  private static final UUID EVENT = UUID.randomUUID();
  private static final UUID CONNECTION = UUID.randomUUID();
  private static final Instant START = Instant.parse("2026-11-20T20:00:00Z");

  @Autowired private MockMvc mockMvc;
  @MockitoBean private AccessCodeConnectionService service;
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
