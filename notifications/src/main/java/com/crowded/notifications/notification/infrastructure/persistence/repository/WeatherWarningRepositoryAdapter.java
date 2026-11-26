package com.crowded.notifications.notification.infrastructure.persistence.repository;

import com.crowded.notifications.notification.domain.model.WeatherWarning;
import com.crowded.notifications.notification.domain.repository.WeatherWarningRepository;
import com.crowded.notifications.notification.infrastructure.persistence.mapper.WeatherWarningPersistenceMapper;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** {@link WeatherWarningRepository} on Core's {@code weather_warning} in PostgreSQL. */
@Repository
class WeatherWarningRepositoryAdapter implements WeatherWarningRepository {

  private final SpringDataWeatherWarningRepository warnings;
  private final WeatherWarningPersistenceMapper mapper;

  WeatherWarningRepositoryAdapter(
      SpringDataWeatherWarningRepository warnings, WeatherWarningPersistenceMapper mapper) {
    this.warnings = warnings;
    this.mapper = mapper;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<WeatherWarning> findById(UUID warningId) {
    return warnings.findWarningById(warningId).map(mapper::toDomain);
  }
}
