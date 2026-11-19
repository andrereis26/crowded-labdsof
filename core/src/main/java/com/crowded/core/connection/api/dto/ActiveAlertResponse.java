package com.crowded.core.connection.api.dto;

import com.crowded.core.event.domain.model.Severity;
import java.time.Instant;
import java.util.UUID;

/** An alert the organization sent and has not resolved. */
public record ActiveAlertResponse(
    UUID id, String type, Severity severity, String message, Instant sentAt) {}
