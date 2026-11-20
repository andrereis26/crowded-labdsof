package com.crowded.core.user.api.mapper;

import com.crowded.core.user.api.dto.ChoosePasswordRequest;
import com.crowded.core.user.api.dto.RegisterRequest;
import com.crowded.core.user.api.dto.RegisteredResponse;
import com.crowded.core.user.api.dto.SignInRequest;
import com.crowded.core.user.api.dto.SignInResponse;
import com.crowded.core.user.api.dto.SignedInUserResponse;
import com.crowded.core.user.application.dto.ChoosePasswordCommand;
import com.crowded.core.user.application.dto.RegisterCommand;
import com.crowded.core.user.application.dto.SignInCommand;
import com.crowded.core.user.application.dto.SignedIn;
import com.crowded.core.user.domain.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps the registration, sign-in and session requests and responses to and from the application
 * layer.
 */
@Mapper
public interface AuthApiMapper {

  RegisterCommand toCommand(RegisterRequest request, String clientAddress);

  SignInCommand toCommand(SignInRequest request, String clientAddress);

  ChoosePasswordCommand toCommand(ChoosePasswordRequest request, String clientAddress);

  @Mapping(target = "userId", source = "id")
  RegisteredResponse toRegisteredResponse(User user);

  /** The session with its tokens, for the phone app. */
  @Mapping(target = "accessToken", source = "token.value")
  @Mapping(target = "expiresAt", source = "token.expiresAt")
  SignInResponse toSignInResponse(SignedIn signedIn);

  /** The session without its tokens, which went into cookies (ADR 0021). */
  @Mapping(target = "accessToken", ignore = true)
  @Mapping(target = "refreshToken", ignore = true)
  @Mapping(target = "expiresAt", source = "token.expiresAt")
  SignInResponse toCookieSignInResponse(SignedIn signedIn);

  SignedInUserResponse toSignedInUser(User user);
}
