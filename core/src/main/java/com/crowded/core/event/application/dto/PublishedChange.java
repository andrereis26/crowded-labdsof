package com.crowded.core.event.application.dto;

import com.crowded.core.event.domain.model.Severity;
import java.util.UUID;

/**
 * The change a command recorded or, when it was sent before, the one recorded the first time.
 *
 * @param created whether this command recorded it
 */
public record PublishedChange(UUID changeId, Severity severity, boolean created) {}
