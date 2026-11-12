package com.crowded.core.user.infrastructure.persistence.mapper;

import com.crowded.core.user.domain.model.Email;
import com.crowded.core.user.domain.model.User;
import com.crowded.core.user.domain.model.UserAccount;
import com.crowded.core.user.infrastructure.persistence.entity.UserEntity;
import org.mapstruct.Mapper;

/** Maps {@code app_user} rows to users and accounts, and accounts to rows. */
@Mapper
public interface UserPersistenceMapper {

  default User toDomain(UserEntity entity) {
    return User.restore(
        entity.getId(), entity.getOrganizationId(), entity.getRole(), entity.isActive());
  }

  default UserAccount toAccount(UserEntity entity) {
    return UserAccount.restore(
        entity.getId(),
        entity.getOrganizationId(),
        entity.getRole(),
        entity.getName(),
        new Email(entity.getEmail()),
        entity.getPasswordHash(),
        entity.isActive(),
        entity.isMustChangePassword(),
        entity.getTemporaryPasswordExpiresAt(),
        entity.getLastSignedInAt(),
        entity.getCreatedAt());
  }

  UserEntity toEntity(UserAccount account);

  default String toText(Email email) {
    return email.value();
  }
}
