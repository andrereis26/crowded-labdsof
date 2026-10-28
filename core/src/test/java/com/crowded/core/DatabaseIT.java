package com.crowded.core;

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
 * Core's view of the database (ADR 0002, ADR 0012): it writes the domain tables, only reads the
 * Notifications tables, cannot change the schema, and finds the migrations and demo data applied.
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
  void appliedEveryMigration() {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE NOT success", Integer.class))
        .isZero();
    assertThat(jdbc.queryForList("SELECT code FROM report_category", String.class))
        .contains("CROWD_LEVEL", "LOST_AND_FOUND");
  }

  @Test
  @ResourceLock(SHARED_STATE)
  void writesItsOwnTables() {
    UUID id = UUID.randomUUID();

    jdbc.update(
        "INSERT INTO organization (id, name, verification_status) VALUES (?, 'Test', 'PENDING')",
        id);
    int deleted = jdbc.update("DELETE FROM organization WHERE id = ?", id);

    assertThat(deleted).isOne();
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
    assertThatThrownBy(() -> jdbc.execute("CREATE TABLE core_test (id integer)"))
        .isInstanceOf(DataAccessException.class)
        .rootCause()
        .hasMessageContaining("permission denied for schema public");
    assertThatThrownBy(() -> jdbc.execute("ALTER TABLE event ADD COLUMN core_test integer"))
        .isInstanceOf(DataAccessException.class)
        .rootCause()
        .hasMessageContaining("must be owner of table event");
  }

  @Test
  void seededTheDemoData() {
    assertThat(jdbc.queryForList("SELECT DISTINCT status::text FROM event", String.class))
        .containsExactlyInAnyOrder(
            "PROPOSED", "SCHEDULED", "POSTPONED", "RELOCATED", "CANCELLED", "ONGOING", "ENDED");
    assertThat(
            jdbc.queryForList(
                "SELECT DISTINCT verification_status::text FROM organization", String.class))
        .contains("VERIFIED", "PENDING");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket", Integer.class)).isPositive();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM follow", Integer.class)).isPositive();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM alert", Integer.class)).isPositive();
  }

  @Test
  void storesDemoPasswordsOnlyAsBcryptHashes() {
    assertThat(jdbc.queryForList("SELECT password_hash FROM app_user", String.class))
        .isNotEmpty()
        .allMatch(hash -> hash.matches("\\{bcrypt}\\$2a\\$10\\$[./A-Za-z0-9]{53}"));
  }
}
