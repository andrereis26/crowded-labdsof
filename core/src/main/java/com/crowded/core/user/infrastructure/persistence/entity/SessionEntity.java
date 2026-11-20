package com.crowded.core.user.infrastructure.persistence.entity;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** A session as it is stored in Redis, as JSON: the hashes of its tokens, never the tokens. */
public record SessionEntity(
    UUID id,
    UUID userId,
    Instant startedAt,
    Instant endsAt,
    Duration idleLifetime,
    String tokenHash,
    String previousTokenHash,
    Instant rotatedAt,
    Instant lastUsedAt) {}
