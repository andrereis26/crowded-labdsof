package com.crowded.core.user.application.dto;

import com.crowded.core.user.domain.model.IssuedToken;
import com.crowded.core.user.domain.model.User;

/** A successful sign-in: the token, and who it is for. */
public record SignedIn(IssuedToken token, User user, boolean mustChangePassword) {}
