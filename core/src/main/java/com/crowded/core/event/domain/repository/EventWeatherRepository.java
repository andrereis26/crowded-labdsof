package com.crowded.core.event.domain.repository;

import com.crowded.core.event.domain.model.EventWeather;
import java.util.UUID;

/** The weather of each event. */
public interface EventWeatherRepository {

  /** The event's weather; {@link EventWeather#none} when IPMA has said nothing about it yet. */
  EventWeather findByEvent(UUID eventId);

  /** Stores the event's weather: its forecast and warnings replace the stored ones. */
  void save(EventWeather weather);
}
