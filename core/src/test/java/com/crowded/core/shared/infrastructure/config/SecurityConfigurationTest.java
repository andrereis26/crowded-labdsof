package com.crowded.core.shared.infrastructure.config;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crowded.core.shared.api.controller.SessionBearerTokenResolver;
import com.crowded.core.shared.api.controller.SessionCookies;
import com.crowded.core.shared.domain.exception.UnavailableException;
import com.crowded.core.user.api.controller.AuthController;
import com.crowded.core.user.api.mapper.AuthApiMapper;
import com.crowded.core.user.application.service.AuthService;
import com.crowded.core.user.application.service.SessionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// The key location only has to resolve: the mocked decoder below replaces the one built from it.
@WebMvcTest(
    controllers = AuthController.class,
    properties = "JWT_PUBLIC_KEY_LOCATION=file:unused.pem")
@Import({
  SecurityConfiguration.class,
  ClockConfiguration.class,
  SessionCookies.class,
  SessionBearerTokenResolver.class
})
class SecurityConfigurationTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private JwtDecoder jwtDecoder;

  // The slice loads only the controller of the public endpoints; its beans are mocked.
  @MockitoBean private AuthService authService;
  @MockitoBean private SessionService sessionService;
  @MockitoBean private AuthApiMapper authApiMapper;

  @Test
  void rejectsRequestsWithoutAToken() throws Exception {
    mockMvc.perform(get("/anything")).andExpect(status().isUnauthorized());
  }

  @Test
  void letsRegistrationAndSignInThroughWithoutATokenOrACsrfToken() throws Exception {
    // An empty body reaches the controller, whose validation refuses it (400), not security.
    for (String path : new String[] {"/auth/register", "/auth/login", "/auth/password"}) {
      mockMvc
          .perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
          .andExpect(status().isBadRequest());
    }
  }

  @Test
  void letsRefreshAndSignOutThroughWithAnExpiredToken() throws Exception {
    when(jwtDecoder.decode("expired")).thenThrow(new BadJwtException("Expired"));

    mockMvc
        .perform(post("/auth/logout").header("Authorization", "Bearer expired"))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            post("/auth/refresh")
                .header("Origin", "http://localhost")
                .cookie(new Cookie("crowded_access", "expired")))
        .andExpect(status().isOk());
  }

  @Test
  void clearsTheCookiesOnSignOutEvenWhenRedisIsDown() throws Exception {
    doThrow(new UnavailableException("SESSIONS_UNAVAILABLE", "Down."))
        .when(sessionService)
        .signOut(any());

    mockMvc
        .perform(
            post("/auth/logout")
                .header("Origin", "http://localhost")
                .cookie(new Cookie("crowded_refresh", "r")))
        .andExpect(status().isServiceUnavailable())
        .andExpect(
            header()
                .stringValues(
                    "Set-Cookie",
                    hasItems(
                        containsString("crowded_access=;"), containsString("crowded_refresh=;"))));
  }

  @Test
  void readsTheTokenFromTheSessionCookie() throws Exception {
    when(jwtDecoder.decode("expired")).thenThrow(new BadJwtException("Expired"));

    mockMvc
        .perform(get("/anything").cookie(new Cookie("crowded_access", "expired")))
        .andExpect(status().isUnauthorized());
    verify(jwtDecoder).decode("expired");
  }

  @Test
  void refusesAChangeFromAnotherOrigin() throws Exception {
    mockMvc
        .perform(
            post("/auth/login")
                .header("Origin", "https://atacante.example")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"));
    // MockMvc's requests go to localhost.
    mockMvc
        .perform(
            post("/auth/login")
                .header("Origin", "http://localhost")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
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
