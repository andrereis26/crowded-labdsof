package com.crowded.core.user.infrastructure.persistence.mapper;

import com.crowded.core.user.domain.model.User;
import com.crowded.core.user.domain.model.UserAccount;
import com.crowded.core.user.infrastructure.persistence.entity.UserEntity;
import org.mapstruct.Mapper;

/** Maps {@code app_user} rows to users and accounts, and accounts to rows. */
@Mapper
public interface UserPersistenceMapper {

  User toDomain(UserEntity entity);

  UserAccount toAccount(UserEntity entity);

  UserEntity toEntity(UserAccount account);
}
