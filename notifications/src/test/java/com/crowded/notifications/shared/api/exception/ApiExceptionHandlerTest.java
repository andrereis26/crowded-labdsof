package com.crowded.notifications.shared.api.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.crowded.notifications.notification.domain.exception.CauseNotFoundException;
import com.crowded.notifications.notification.domain.exception.NotificationNotFoundException;
import com.crowded.notifications.shared.domain.exception.NotAuthenticatedException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

class ApiExceptionHandlerTest {

  private final ApiExceptionHandler handler = new ApiExceptionHandler();

  @Test
  void answersEachRefusalWithItsStatusAndCode() {
    ProblemDetail notFound = handler.handle(new NotificationNotFoundException(UUID.randomUUID()));
    ProblemDetail unauthenticated =
        handler.handle(new NotAuthenticatedException("INVALID_TOKEN", "No user."));
    ProblemDetail other =
        handler.handle(new CauseNotFoundException(UUID.randomUUID(), UUID.randomUUID()));

    assertThat(notFound.getStatus()).isEqualTo(404);
    assertThat(notFound.getProperties()).containsEntry("code", "NOTIFICATION_NOT_FOUND");
    assertThat(unauthenticated.getStatus()).isEqualTo(401);
    assertThat(other.getStatus()).isEqualTo(400);
  }
}
