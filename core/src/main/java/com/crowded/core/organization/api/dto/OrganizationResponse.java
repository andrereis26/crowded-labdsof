package com.crowded.core.organization.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** An organization in {@code GET /admin/organizations}. */
public record OrganizationResponse(
    UUID id,
    String name,
    Instant createdAt,
    long eventCount,
    List<OrganizationMemberResponse> members) {

  public OrganizationResponse {
    members = List.copyOf(members);
  }
}
