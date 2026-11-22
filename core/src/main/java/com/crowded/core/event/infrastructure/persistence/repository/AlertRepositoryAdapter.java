package com.crowded.core.event.infrastructure.persistence.repository;

import com.crowded.core.event.domain.model.Alert;
import com.crowded.core.event.domain.repository.AlertRepository;
import com.crowded.core.event.infrastructure.persistence.mapper.AlertPersistenceMapper;
import org.springframework.stereotype.Repository;

/** {@link AlertRepository} on PostgreSQL. */
@Repository
class AlertRepositoryAdapter implements AlertRepository {

  private final SpringDataAlertRepository alerts;
  private final AlertPersistenceMapper mapper;

  AlertRepositoryAdapter(SpringDataAlertRepository alerts, AlertPersistenceMapper mapper) {
    this.alerts = alerts;
    this.mapper = mapper;
  }

  @Override
  public void save(Alert alert) {
    alerts.save(mapper.toEntity(alert));
  }
}
