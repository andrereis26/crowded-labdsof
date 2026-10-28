package com.crowded.core;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// The key location only has to resolve: the mocked decoder below replaces the one built from it.
@WebMvcTest(properties = "JWT_PUBLIC_KEY_LOCATION=file:unused.pem")
@Import(SecurityConfiguration.class)
class SecurityConfigurationTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private JwtDecoder jwtDecoder;

  @Test
  void rejectsRequestsWithoutAToken() throws Exception {
    mockMvc.perform(get("/anything")).andExpect(status().isUnauthorized());
  }

  @Test
  void letsHealthChecksThroughWithoutAToken() throws Exception {
    // Actuator is not part of this slice, so the permitted request finds no handler (404)
    // instead of being rejected (401). ApplicationIT checks the real endpoint.
    mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isNotFound());
  }
}
