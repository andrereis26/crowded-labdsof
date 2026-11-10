package com.crowded.core.shared.api.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.crowded.core.shared.domain.exception.DomainException;
import java.io.Serial;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

class ApiExceptionHandlerTest {

  @Test
  void answersAnUnknownKindOfRefusalAsABadRequest() {
    ProblemDetail problem = new ApiExceptionHandler().handle(new OtherRefusal());

    assertThat(problem.getStatus()).isEqualTo(400);
    assertThat(problem.getProperties()).containsEntry("code", "OTHER");
  }

  private static final class OtherRefusal extends DomainException {

    @Serial private static final long serialVersionUID = 1L;

    OtherRefusal() {
      super("OTHER", "Something else.");
    }
  }
}
