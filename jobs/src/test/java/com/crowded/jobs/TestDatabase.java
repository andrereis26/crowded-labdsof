package com.crowded.jobs;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Migrates the test PostgreSQL the way a deployment does (ADR 0012): the crowded-database image,
 * built from {@code ../database}, applies the migrations, the service users, the grants and the
 * demo data. The service then connects as its own user, so a test fails on a write to a table this
 * service does not own.
 */
final class TestDatabase {

  /** This service's database user, created by the migrations. */
  static final String USERNAME = "crowded_jobs";

  /** Network alias of PostgreSQL, which the migration container connects to. */
  static final String POSTGRES_HOST = "postgres";

  private static final Path PROJECT = Path.of("..", "database");
  private static final SecureRandom RANDOM = new SecureRandom();

  private final Map<String, String> passwords =
      Map.of(
          "CORE_DB_PASSWORD", randomPassword(),
          "JOBS_DB_PASSWORD", randomPassword(),
          "NOTIFICATIONS_DB_PASSWORD", randomPassword());

  private TestDatabase() {}

  /** Runs the migrations, with the demo data, against PostgreSQL on the given network. */
  static TestDatabase migrate(Network network, PostgreSQLContainer postgres) {
    TestDatabase database = new TestDatabase();
    try (GenericContainer<?> flyway =
        new GenericContainer<>(
                new ImageFromDockerfile("crowded-database-test", false)
                    .withFileFromPath(".", PROJECT))
            .withNetwork(network)
            .withEnv(
                "DB_URL",
                "jdbc:postgresql://" + POSTGRES_HOST + ":5432/" + postgres.getDatabaseName())
            .withEnv("DB_USERNAME", postgres.getUsername())
            .withEnv("DB_PASSWORD", postgres.getPassword())
            .withEnv(database.passwords)
            .withEnv("SEED_DEMO_DATA", "true")
            // Started means exited with status 0; a failed migration fails every test.
            .withStartupCheckStrategy(
                new OneShotStartupCheckStrategy().withTimeout(Duration.ofMinutes(3)))) {
      flyway.start();
    }
    return database;
  }

  /** Password of {@link #USERNAME}. */
  String password() {
    return passwords.get("JOBS_DB_PASSWORD");
  }

  // Letters and digits only, as the migration image requires.
  private static String randomPassword() {
    byte[] bytes = new byte[16];
    RANDOM.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
