package com.crowded.core.event.application.dto;

import java.util.UUID;

/**
 * Jobs' {@code WeatherUpdated}: a district's weather changed (API Outline, Messages).
 *
 * @param snapshotId the snapshot Jobs stored, also the message id
 */
public record ApplyWeatherCommand(String district, UUID snapshotId) {}
