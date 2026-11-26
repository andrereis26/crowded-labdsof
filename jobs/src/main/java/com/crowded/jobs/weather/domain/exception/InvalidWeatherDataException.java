package com.crowded.jobs.weather.domain.exception;

/**
 * What IPMA sent does not make a valid warning or forecast. It is rejected and logged, and never
 * replaces the last valid data.
 */
public class InvalidWeatherDataException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvalidWeatherDataException(String message) {
    super(message);
  }

  public InvalidWeatherDataException(String message, Throwable cause) {
    super(message, cause);
  }
}
