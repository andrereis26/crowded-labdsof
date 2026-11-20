package com.crowded.notifications;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crowded.notifications.notification.api.controller.NotificationController;
import com.crowded.notifications.notification.api.mapper.NotificationApiMapper;
import com.crowded.notifications.notification.application.service.NotificationCenter;
import com.crowded.notifications.shared.api.controller.SessionBearerTokenResolver;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// The key location only has to resolve: the mocked decoder below replaces the one built from it.
@WebMvcTest(
    controllers = NotificationController.class,
    properties = "JWT_PUBLIC_KEY_LOCATION=file:unused.pem")
@Import({SecurityConfiguration.class, SessionBearerTokenResolver.class})
class SecurityConfigurationTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private JwtDecoder jwtDecoder;

  // The slice loads one controller; its beans are mocked.
  @MockitoBean private NotificationCenter center;
  @MockitoBean private NotificationApiMapper mapper;

  @Test
  void rejectsRequestsWithoutAToken() throws Exception {
    mockMvc.perform(get("/anything")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/notifications")).andExpect(status().isUnauthorized());
  }

  @Test
  void readsTheTokenFromTheSessionCookie() throws Exception {
    when(jwtDecoder.decode("expired")).thenThrow(new BadJwtException("Expired"));

    mockMvc
        .perform(get("/notifications").cookie(new Cookie("crowded_access", "expired")))
        .andExpect(status().isUnauthorized());
    verify(jwtDecoder).decode("expired");
  }

  @Test
  void refusesAChangeFromAnotherOriginOrWithTheCookieAndNoOrigin() throws Exception {
    String path = "/notifications/" + UUID.randomUUID() + "/read";
    mockMvc
        .perform(post(path).header("Origin", "https://atacante.example"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"));
    mockMvc
        .perform(post(path).cookie(new Cookie("crowded_access", "t")))
        .andExpect(status().isForbidden());
    // From the same host, the request reaches security, which wants a token.
    mockMvc
        .perform(post(path).header("Origin", "http://localhost"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void letsHealthChecksThroughWithoutAToken() throws Exception {
    // Actuator is not part of this slice, so the permitted request finds no handler (404)
    // instead of being rejected (401). ApplicationIT checks the real endpoint.
    mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isNotFound());
  }
}
