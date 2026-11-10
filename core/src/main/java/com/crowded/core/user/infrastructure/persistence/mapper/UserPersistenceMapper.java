package com.crowded.core.user.infrastructure.persistence.mapper;

import com.crowded.core.user.domain.model.User;
import com.crowded.core.user.infrastructure.persistence.entity.UserEntity;
import org.mapstruct.Mapper;

/** Maps {@code app_user} rows to users. */
@Mapper
public interface UserPersistenceMapper {

  User toDomain(UserEntity entity);
}
