package com.crowded.core.connection.infrastructure.persistence.repository;

import com.crowded.core.connection.domain.model.ActiveAlert;
import com.crowded.core.connection.domain.repository.ActiveAlertRepository;
import com.crowded.core.event.domain.model.Severity;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link ActiveAlertRepository} on PostgreSQL. It only reads {@code alert}, which the module that
 * sends alerts owns.
 */
@Repository
class ActiveAlertRepositoryAdapter implements ActiveAlertRepository {

  private static final String ACTIVE_ALERTS =
      """
      SELECT id, event_id, type::text AS type, severity::text AS severity, message, sent_at
      FROM alert
      WHERE event_id IN (:eventIds) AND status = 'SENT' AND resolved_at IS NULL
      ORDER BY sent_at DESC, id DESC
      """;

  private final JdbcClient jdbc;

  ActiveAlertRepositoryAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Map<UUID, List<ActiveAlert>> findByEvents(Collection<UUID> eventIds) {
    if (eventIds.isEmpty()) {
      return Map.of();
    }
    return jdbc
        .sql(ACTIVE_ALERTS)
        .param("eventIds", eventIds)
        .query(
            (rows, number) ->
                Map.entry(
                    rows.getObject("event_id", UUID.class),
                    new ActiveAlert(
                        rows.getObject("id", UUID.class),
                        rows.getString("type"),
                        Severity.valueOf(rows.getString("severity")),
                        rows.getString("message"),
                        rows.getObject("sent_at", OffsetDateTime.class).toInstant())))
        .list()
        .stream()
        .collect(
            Collectors.groupingBy(
                Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
  }
}
