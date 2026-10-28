package com.crowded.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Notifications' view of the database (ADR 0002, ADR 0012): it writes notifications and
 * preferences, only reads Core's tables, and cannot change the schema.
 */
class DatabaseIT extends IntegrationTest {

  // A participant and an event of the demo data (database/seed/R__demo_data.sql).
  private static final UUID DEMO_USER = UUID.fromString("10000000-0000-4000-8000-000000000011");
  private static final UUID DEMO_EVENT = UUID.fromString("30000000-0000-4000-8000-000000000002");

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
  @ResourceLock(SHARED_STATE)
  void writesItsOwnTables() {
    UUID id = UUID.randomUUID();

    jdbc.update(
        """
        INSERT INTO notification (id, user_id, event_id, type, title, body, deduplication_key, \
        created_at) VALUES (?, ?, ?, 'OPERATIONAL_ALERT', 'Test', 'Test', ?, now())""",
        id,
        DEMO_USER,
        DEMO_EVENT,
        "test-" + id);
    int deleted = jdbc.update("DELETE FROM notification WHERE id = ?", id);

    assertThat(deleted).isOne();
  }

  @Test
  void readsButCannotWriteCoreTables() {
    assertThat(jdbc.queryForObject("SELECT count(*) FROM event_connection", Integer.class))
        .isPositive();
    assertThatThrownBy(() -> jdbc.update("DELETE FROM event_connection"))
        .isInstanceOf(DataAccessException.class)
        .rootCause()
        .hasMessageContaining("permission denied for table event_connection");
  }

  @Test
  void cannotChangeTheSchema() {
    assertThatThrownBy(() -> jdbc.execute("CREATE TABLE notifications_test (id integer)"))
        .isInstanceOf(DataAccessException.class)
        .rootCause()
        .hasMessageContaining("permission denied for schema public");
    assertThatThrownBy(() -> jdbc.execute("TRUNCATE notification"))
        .isInstanceOf(DataAccessException.class)
        .rootCause()
        .hasMessageContaining("permission denied for table notification");
  }
}
