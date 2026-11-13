package com.crowded.core.event.api.dto;

import java.util.UUID;

/** The organization that owns an event, the source of its details. */
public record OrganizationSummaryResponse(UUID id, String name) {}
