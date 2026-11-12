package com.crowded.core.user.api.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.crowded.core.user.api.dto.AddMemberRequest;
import com.crowded.core.user.api.dto.ChangeMemberRequest;
import com.crowded.core.user.api.dto.MemberChangedResponse;
import com.crowded.core.user.api.dto.TemporaryPasswordResponse;
import com.crowded.core.user.application.dto.AddMemberCommand;
import com.crowded.core.user.application.dto.ChangeMemberCommand;
import com.crowded.core.user.application.dto.MemberChanged;
import com.crowded.core.user.application.dto.TemporaryPassword;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MemberAdministrationApiMapperTest {

  private static final UUID ADMIN = UUID.randomUUID();
  private static final UUID ID = UUID.randomUUID();
  private static final Instant EXPIRES = Instant.parse("2026-11-19T10:00:00Z");

  private final MemberAdministrationApiMapper mapper = new MemberAdministrationApiMapperImpl();

  @Test
  void mapsTheRequestsWithTheAdministratorAndTheTarget() {
    assertThat(mapper.toCommand(ADMIN, ID, new AddMemberRequest("Rita", "rita@x.pt")))
        .isEqualTo(new AddMemberCommand(ADMIN, ID, "Rita", "rita@x.pt"));
    assertThat(mapper.toCommand(ADMIN, ID, new ChangeMemberRequest(false, null)))
        .isEqualTo(new ChangeMemberCommand(ADMIN, ID, false, null));
  }

  @Test
  void mapsTheTemporaryPasswords() {
    assertThat(mapper.toResponse(new TemporaryPassword(ID, "T7q-mK4-v9Px", EXPIRES)))
        .isEqualTo(new TemporaryPasswordResponse(ID, "T7q-mK4-v9Px", EXPIRES));
    assertThat(mapper.toResponse(new MemberChanged(ID, true, null, null)))
        .isEqualTo(new MemberChangedResponse(ID, true, null, null));
  }
}
