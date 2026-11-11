package com.crowded.core.user.application.dto;

/** A participant's registration, from the client at {@code clientAddress}. */
public record RegisterCommand(String email, String password, String clientAddress) {}
