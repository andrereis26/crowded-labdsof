package com.crowded.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Jobs' view of the database (ADR 0002, ADR 0012): it reads every table, owns none of the data
 * model yet (Core applies what Jobs detects), and cannot change the schema.
 */
class DatabaseIT extends IntegrationTest {

  @Autowired private JdbcTemplate jdbc;
  @Autowired private Environment environment;

  @Test
  void connectsAsItsOwnUser() {
    assertThat(jdbc.queryForObject("SELECT current_user", String.class))
        .isEqualTo(TestDatabase.USERNAME);
  }

  @Test
  void onlyValidatesTheSchema() {
    assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
  }

  @Test
  void readsButCannotWriteCoreTables() {
    assertThat(jdbc.queryForObject("SELECT count(*) FROM event", Integer.class)).isPositive();
    assertThatThrownBy(() -> jdbc.update("UPDATE event SET district = district"))
        .isInstanceOf(DataAccessException.class)
        .rootCause()
        .hasMessageContaining("permission denied for table event");
  }

  @Test
  void readsButCannotWriteNotificationsTables() {
    assertThat(jdbc.queryForObject("SELECT count(*) FROM notification", Integer.class))
        .isPositive();
    assertThatThrownBy(() -> jdbc.update("DELETE FROM notification"))
        .isInstanceOf(DataAccessException.class)
        .rootCause()
        .hasMessageContaining("permission denied for table notification");
  }

  @Test
  void cannotChangeTheSchema() {
    assertThatThrownBy(() -> jdbc.execute("CREATE TABLE jobs_test (id integer)"))
        .isInstanceOf(DataAccessException.class)
        .rootCause()
        .hasMessageContaining("permission denied for schema public");
    assertThatThrownBy(() -> jdbc.execute("DROP TABLE weather_forecast"))
        .isInstanceOf(DataAccessException.class)
        .rootCause()
        .hasMessageContaining("must be owner of table weather_forecast");
  }
}
