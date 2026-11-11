package com.crowded.core.user.application.dto;

/** A sign-in attempt, from the client at {@code clientAddress}. */
public record SignInCommand(String email, String password, String clientAddress) {}
