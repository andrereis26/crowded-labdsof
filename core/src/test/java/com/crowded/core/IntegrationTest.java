package com.crowded.core;

import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ;

import java.util.UUID;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/**
 * Base class of every integration test (ADR 0011). All of them share one set of containers, started
 * once per test run, and one Spring context. PostgreSQL is migrated by the crowded-database image
 * with the demo data, and the service connects as its own database user ({@link TestDatabase}):
 *
 * <ul>
 *   <li>Do not add {@code @MockitoBean}, {@code @TestPropertySource} or {@code @DirtiesContext} to
 *       a subclass: each one starts another context.
 *   <li>Tests run in parallel. A test that only reads the shared state inherits the {@link
 *       #SHARED_STATE} read lock and runs alongside the other readers.
 *   <li>A test that changes the shared state (database, broker or cache) declares
 *       {@code @ResourceLock(SHARED_STATE)}, so it runs alone. It creates its own data with unique
 *       ids and never assumes an empty table.
 * </ul>
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ResourceLock(value = IntegrationTest.SHARED_STATE, mode = READ)
public abstract class IntegrationTest {

  /** Lock on the database, the broker and the cache that every integration test shares. */
  public static final String SHARED_STATE = "shared-state";

  // Same versions as deployment/docker-compose.swarm.yml.
  private static final Network NETWORK = Network.newNetwork();
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:18-alpine")
          .withNetwork(NETWORK)
          .withNetworkAliases(TestDatabase.POSTGRES_HOST);
  private static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4.1-alpine");
  private static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:8.2-alpine").withExposedPorts(6379);
  private static final TestJwtKeys JWT_KEYS = TestJwtKeys.generate();
  private static final TestDatabase DATABASE;

  static {
    // Stopped by Testcontainers when the test JVM exits.
    Startables.deepStart(POSTGRES, RABBITMQ, REDIS).join();
    DATABASE = TestDatabase.migrate(NETWORK, POSTGRES);
  }

  /** A token of the user {@code userId}, signed with the key pair Core validates against. */
  protected static String token(UUID userId) {
    return JWT_KEYS.token(userId.toString());
  }

  @DynamicPropertySource
  static void connectToContainers(DynamicPropertyRegistry registry) {
    registry.add("DB_URL", POSTGRES::getJdbcUrl);
    registry.add("DB_USERNAME", () -> TestDatabase.USERNAME);
    registry.add("DB_PASSWORD", DATABASE::password);
    registry.add("RABBITMQ_HOST", RABBITMQ::getHost);
    registry.add("RABBITMQ_PORT", RABBITMQ::getAmqpPort);
    registry.add("RABBITMQ_USERNAME", RABBITMQ::getAdminUsername);
    registry.add("RABBITMQ_PASSWORD", RABBITMQ::getAdminPassword);
    registry.add("REDIS_HOST", REDIS::getHost);
    registry.add("REDIS_PORT", () -> REDIS.getMappedPort(6379));
    registry.add("JWT_PUBLIC_KEY_LOCATION", JWT_KEYS::publicKeyLocation);
    registry.add("JWT_PRIVATE_KEY_LOCATION", () -> JWT_KEYS.privateKeyLocation());
  }
}
