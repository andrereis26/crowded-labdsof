package com.crowded.core.organization.application.dto;

import com.crowded.core.user.application.dto.MemberOverview;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** An organization as the administration page lists it, with its members. */
public record OrganizationOverview(
    UUID id, String name, Instant createdAt, long eventCount, List<MemberOverview> members) {

  public OrganizationOverview {
    members = List.copyOf(members);
  }
}
