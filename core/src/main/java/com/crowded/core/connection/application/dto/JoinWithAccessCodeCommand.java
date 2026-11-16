package com.crowded.core.connection.application.dto;

import java.util.UUID;

/**
 * A participant's request to connect to a public event.
 *
 * @param accessCode as typed or read from the QR code, before it is normalized
 */
public record JoinWithAccessCodeCommand(UUID userId, String accessCode) {}
