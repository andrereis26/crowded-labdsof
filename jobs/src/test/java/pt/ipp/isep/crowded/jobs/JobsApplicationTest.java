package pt.ipp.isep.crowded.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Placeholder unit test: it starts no Spring context, so it needs no database, broker or cache. */
class JobsApplicationTest {

  @Test
  void isASpringBootApplication() {
    assertThat(JobsApplication.class).hasAnnotation(SpringBootApplication.class);
  }
}
