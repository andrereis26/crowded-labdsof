package com.crowded.core.user.api.mapper;

import com.crowded.core.user.api.dto.AccountResponse;
import com.crowded.core.user.application.dto.AccountOverview;
import org.mapstruct.Mapper;

/** Maps the signed-in user's account to {@code GET /me}. */
@Mapper
public interface AccountApiMapper {

  AccountResponse toResponse(AccountOverview account);
}
