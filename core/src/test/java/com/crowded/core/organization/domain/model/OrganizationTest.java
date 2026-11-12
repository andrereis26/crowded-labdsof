package com.crowded.core.organization.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crowded.core.shared.domain.exception.RuleViolationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrganizationTest {

  private static final UUID ADMIN = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");

  @Test
  void createsAnOrganizationWithAStrippedName() {
    Organization organization = Organization.create("  Festival Paredes ", ADMIN, NOW);

    assertThat(organization.getName()).isEqualTo("Festival Paredes");
    assertThat(organization.getCreatedByUserId()).isEqualTo(ADMIN);
    assertThat(organization.getCreatedAt()).isEqualTo(NOW);
    assertThat(organization.getId()).isNotNull();
  }

  @Test
  void needsANameOfAtMost120Characters() {
    assertRefused(null, "NAME_REQUIRED");
    assertRefused("   ", "NAME_REQUIRED");
    assertRefused("x".repeat(121), "NAME_TOO_LONG");
    assertThat(Organization.create("x".repeat(120), ADMIN, NOW)).isNotNull();
  }

  @Test
  void isTheSameOrganizationByItsId() {
    Organization organization = Organization.create("Ribeira Live", ADMIN, NOW);

    assertThat(organization)
        .isEqualTo(Organization.restore(organization.getId(), "Renamed", null, NOW))
        .isNotEqualTo(Organization.create("Ribeira Live", ADMIN, NOW));
  }

  private static void assertRefused(String name, String code) {
    assertThatThrownBy(() -> Organization.create(name, ADMIN, NOW))
        .isInstanceOfSatisfying(
            RuleViolationException.class, e -> assertThat(e.getCode()).isEqualTo(code));
  }
}
