package com.crowded.core.organization.api.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.crowded.core.organization.api.dto.CreateOrganizationRequest;
import com.crowded.core.organization.api.dto.OrganizationMemberResponse;
import com.crowded.core.organization.api.dto.OrganizationResponse;
import com.crowded.core.organization.application.dto.CreateOrganizationCommand;
import com.crowded.core.organization.application.dto.OrganizationOverview;
import com.crowded.core.user.application.dto.MemberOverview;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrganizationAdministrationApiMapperTest {

  private final OrganizationAdministrationApiMapper mapper =
      new OrganizationAdministrationApiMapperImpl();

  @Test
  void mapsTheRequestWithTheAdministrator() {
    UUID admin = UUID.randomUUID();

    assertThat(mapper.toCommand(admin, new CreateOrganizationRequest("Ribeira Live")))
        .isEqualTo(new CreateOrganizationCommand(admin, "Ribeira Live"));
  }

  @Test
  void mapsTheOrganizationsWithTheirMembersWithoutCredentials() {
    Instant now = Instant.parse("2026-11-12T10:00:00Z");
    UUID organization = UUID.randomUUID();
    MemberOverview rita =
        new MemberOverview(
            UUID.randomUUID(), organization, "Rita", "rita@x.pt", true, true, now, null);

    assertThat(
            mapper.toResponses(
                List.of(new OrganizationOverview(organization, "Ribeira", now, 3, List.of(rita)))))
        .containsExactly(
            new OrganizationResponse(
                organization,
                "Ribeira",
                now,
                3,
                List.of(
                    new OrganizationMemberResponse(
                        rita.userId(), "Rita", "rita@x.pt", true, true, now, null))));
  }
}
