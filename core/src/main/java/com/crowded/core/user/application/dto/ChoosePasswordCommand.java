package com.crowded.core.user.application.dto;

/**
 * A member replacing their temporary password with {@code newPassword}, from the client at {@code
 * clientAddress}.
 */
public record ChoosePasswordCommand(
    String email, String temporaryPassword, String newPassword, String clientAddress) {}
