package com.crowded.core.event.infrastructure.persistence.repository;

import com.crowded.core.event.domain.model.EventWeather;
import com.crowded.core.event.domain.model.WeatherForecast;
import com.crowded.core.event.domain.model.WeatherWarning;
import com.crowded.core.event.domain.repository.EventWeatherRepository;
import com.crowded.core.event.infrastructure.persistence.mapper.WeatherPersistenceMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link EventWeatherRepository} on {@code weather_forecast} and {@code weather_warning} in
 * PostgreSQL. The forecast is replaced as a whole; a warning keeps its row, and so what was
 * notified, while IPMA keeps it.
 */
@Repository
class EventWeatherRepositoryAdapter implements EventWeatherRepository {

  private final SpringDataWeatherForecastRepository forecasts;
  private final SpringDataWeatherWarningRepository warnings;
  private final WeatherPersistenceMapper mapper;

  EventWeatherRepositoryAdapter(
      SpringDataWeatherForecastRepository forecasts,
      SpringDataWeatherWarningRepository warnings,
      WeatherPersistenceMapper mapper) {
    this.forecasts = forecasts;
    this.warnings = warnings;
    this.mapper = mapper;
  }

  @Override
  @Transactional(readOnly = true)
  public EventWeather findByEvent(UUID eventId) {
    return EventWeather.restore(
        eventId,
        forecasts.findByEventId(eventId).stream().map(mapper::toDomain).toList(),
        warnings.findByEventId(eventId).stream().map(mapper::toDomain).toList());
  }

  @Override
  @Transactional
  public void save(EventWeather weather) {
    UUID eventId = weather.getEventId();
    forecasts.deleteByEvent(eventId);
    for (WeatherForecast day : weather.getForecast()) {
      forecasts.save(mapper.toEntity(UUID.randomUUID(), eventId, day));
    }
    List<WeatherWarning> current = weather.getWarnings();
    if (current.isEmpty()) {
      warnings.deleteByEvent(eventId);
    } else {
      warnings.deleteByEventExcept(eventId, current.stream().map(WeatherWarning::getId).toList());
    }
    current.forEach(warning -> warnings.save(mapper.toEntity(warning)));
  }
}
