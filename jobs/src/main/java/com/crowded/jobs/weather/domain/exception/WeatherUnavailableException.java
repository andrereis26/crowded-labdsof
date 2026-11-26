package com.crowded.jobs.weather.domain.exception;

/** IPMA did not answer, even after the retries of the run; the next run asks again. */
public class WeatherUnavailableException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public WeatherUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
