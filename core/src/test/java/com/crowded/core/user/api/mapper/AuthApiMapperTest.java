package com.crowded.core.user.api.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.crowded.core.user.api.dto.RegisterRequest;
import com.crowded.core.user.api.dto.RegisteredResponse;
import com.crowded.core.user.api.dto.SignInRequest;
import com.crowded.core.user.api.dto.SignInResponse;
import com.crowded.core.user.api.dto.SignedInUserResponse;
import com.crowded.core.user.application.dto.RegisterCommand;
import com.crowded.core.user.application.dto.SignInCommand;
import com.crowded.core.user.application.dto.SignedIn;
import com.crowded.core.user.domain.model.IssuedToken;
import com.crowded.core.user.domain.model.Role;
import com.crowded.core.user.domain.model.User;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthApiMapperTest {

  private final AuthApiMapper mapper = new AuthApiMapperImpl();

  @Test
  void mapsTheRequestsWithTheClientAddress() {
    assertThat(mapper.toCommand(new RegisterRequest("a@b.pt", "segredo-123"), "203.0.113.7"))
        .isEqualTo(new RegisterCommand("a@b.pt", "segredo-123", "203.0.113.7"));
    assertThat(mapper.toCommand(new SignInRequest("a@b.pt", "segredo-123"), "203.0.113.7"))
        .isEqualTo(new SignInCommand("a@b.pt", "segredo-123", "203.0.113.7"));
  }

  @Test
  void mapsTheAccountAndTheSignIn() {
    UUID id = UUID.randomUUID();
    UUID organization = UUID.randomUUID();
    User member = new User(id, organization, Role.ORGANIZATION_MEMBER, true);
    Instant expiresAt = Instant.parse("2026-11-12T11:00:00Z");

    assertThat(mapper.toRegisteredResponse(member))
        .isEqualTo(new RegisteredResponse(id, "ORGANIZATION_MEMBER"));
    assertThat(
            mapper.toSignInResponse(new SignedIn(new IssuedToken("jwt", expiresAt), member, false)))
        .isEqualTo(
            new SignInResponse(
                "jwt",
                expiresAt,
                false,
                new SignedInUserResponse(id, "ORGANIZATION_MEMBER", organization)));
  }
}
