package com.crowded.core.organization.api.mapper;

import com.crowded.core.organization.api.dto.CreateOrganizationRequest;
import com.crowded.core.organization.api.dto.OrganizationMemberResponse;
import com.crowded.core.organization.api.dto.OrganizationResponse;
import com.crowded.core.organization.application.dto.CreateOrganizationCommand;
import com.crowded.core.organization.application.dto.OrganizationOverview;
import com.crowded.core.user.application.dto.MemberOverview;
import java.util.List;
import java.util.UUID;
import org.mapstruct.Mapper;

/** Maps the organization administration requests and responses. */
@Mapper
public interface OrganizationAdministrationApiMapper {

  CreateOrganizationCommand toCommand(UUID administratorId, CreateOrganizationRequest request);

  List<OrganizationResponse> toResponses(List<OrganizationOverview> organizations);

  OrganizationResponse toResponse(OrganizationOverview organization);

  OrganizationMemberResponse toResponse(MemberOverview member);
}
