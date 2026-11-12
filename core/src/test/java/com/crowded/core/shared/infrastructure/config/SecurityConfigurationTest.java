package com.crowded.core.shared.infrastructure.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crowded.core.user.api.controller.AuthController;
import com.crowded.core.user.api.mapper.AuthApiMapper;
import com.crowded.core.user.application.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// The key location only has to resolve: the mocked decoder below replaces the one built from it.
@WebMvcTest(
    controllers = AuthController.class,
    properties = "JWT_PUBLIC_KEY_LOCATION=file:unused.pem")
@Import(SecurityConfiguration.class)
class SecurityConfigurationTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private JwtDecoder jwtDecoder;

  // The slice loads only the controller of the public endpoints; its beans are mocked.
  @MockitoBean private AuthService authService;
  @MockitoBean private AuthApiMapper authApiMapper;

  @Test
  void rejectsRequestsWithoutAToken() throws Exception {
    mockMvc.perform(get("/anything")).andExpect(status().isUnauthorized());
  }

  @Test
  void letsRegistrationAndSignInThroughWithoutATokenOrACsrfToken() throws Exception {
    // An empty body reaches the controller, whose validation refuses it (400), not security.
    for (String path : new String[] {"/auth/register", "/auth/login"}) {
      mockMvc
          .perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
          .andExpect(status().isBadRequest());
    }
  }

  @Test
  void requiresATokenForOtherMethodsOnTheAuthPaths() throws Exception {
    mockMvc.perform(get("/auth/login")).andExpect(status().isUnauthorized());
  }

  @Test
  void letsHealthChecksThroughWithoutAToken() throws Exception {
    // Actuator is not part of this slice, so the permitted request finds no handler (404)
    // instead of being rejected (401). ApplicationIT checks the real endpoint.
    mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isNotFound());
  }
}
