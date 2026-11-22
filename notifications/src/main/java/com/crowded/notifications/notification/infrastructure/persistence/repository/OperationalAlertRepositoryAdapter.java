package com.crowded.notifications.notification.infrastructure.persistence.repository;

import com.crowded.notifications.notification.domain.model.OperationalAlert;
import com.crowded.notifications.notification.domain.repository.OperationalAlertRepository;
import com.crowded.notifications.notification.infrastructure.persistence.mapper.OperationalAlertPersistenceMapper;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** {@link OperationalAlertRepository} on Core's {@code alert} in PostgreSQL. */
@Repository
class OperationalAlertRepositoryAdapter implements OperationalAlertRepository {

  private final SpringDataOperationalAlertRepository alerts;
  private final OperationalAlertPersistenceMapper mapper;

  OperationalAlertRepositoryAdapter(
      SpringDataOperationalAlertRepository alerts, OperationalAlertPersistenceMapper mapper) {
    this.alerts = alerts;
    this.mapper = mapper;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<OperationalAlert> findById(UUID alertId) {
    return alerts.findById(alertId).map(mapper::toDomain);
  }
}
