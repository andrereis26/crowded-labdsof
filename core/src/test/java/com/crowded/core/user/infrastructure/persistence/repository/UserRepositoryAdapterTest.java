package com.crowded.core.user.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crowded.core.user.domain.model.Role;
import com.crowded.core.user.domain.model.User;
import com.crowded.core.user.infrastructure.persistence.entity.UserEntity;
import com.crowded.core.user.infrastructure.persistence.mapper.UserPersistenceMapperImpl;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserRepositoryAdapterTest {

  @Test
  void readsTheRowAsAUser() {
    SpringDataUserRepository rows = mock(SpringDataUserRepository.class);
    UserEntity row = new UserEntity();
    row.setId(UUID.randomUUID());
    row.setOrganizationId(UUID.randomUUID());
    row.setRole(Role.ORGANIZATION_MEMBER);
    when(rows.findById(row.getId())).thenReturn(Optional.of(row));

    assertThat(
            new UserRepositoryAdapter(rows, new UserPersistenceMapperImpl()).findById(row.getId()))
        .contains(new User(row.getId(), row.getOrganizationId(), Role.ORGANIZATION_MEMBER));
  }
}
