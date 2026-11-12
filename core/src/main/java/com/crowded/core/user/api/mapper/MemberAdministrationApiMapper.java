package com.crowded.core.user.api.mapper;

import com.crowded.core.user.api.dto.AddMemberRequest;
import com.crowded.core.user.api.dto.ChangeMemberRequest;
import com.crowded.core.user.api.dto.MemberChangedResponse;
import com.crowded.core.user.api.dto.TemporaryPasswordResponse;
import com.crowded.core.user.application.dto.AddMemberCommand;
import com.crowded.core.user.application.dto.ChangeMemberCommand;
import com.crowded.core.user.application.dto.MemberChanged;
import com.crowded.core.user.application.dto.TemporaryPassword;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps the member administration requests and responses. */
@Mapper
public interface MemberAdministrationApiMapper {

  AddMemberCommand toCommand(UUID administratorId, UUID organizationId, AddMemberRequest request);

  ChangeMemberCommand toCommand(UUID administratorId, UUID userId, ChangeMemberRequest request);

  @Mapping(target = "temporaryPassword", source = "password")
  TemporaryPasswordResponse toResponse(TemporaryPassword password);

  MemberChangedResponse toResponse(MemberChanged member);
}
