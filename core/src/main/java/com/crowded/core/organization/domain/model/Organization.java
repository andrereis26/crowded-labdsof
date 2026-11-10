package com.crowded.core.organization.domain.model;

import java.util.UUID;

/** An organization, as far as Core needs it so far; #140 adds the rest. */
public record Organization(UUID id, String name) {}
