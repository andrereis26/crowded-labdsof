package com.crowded.core.user.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crowded.core.shared.domain.exception.ConflictException;
import com.crowded.core.user.domain.model.Email;
import com.crowded.core.user.domain.model.Role;
import com.crowded.core.user.domain.model.User;
import com.crowded.core.user.domain.model.UserAccount;
import com.crowded.core.user.infrastructure.persistence.entity.UserEntity;
import com.crowded.core.user.infrastructure.persistence.mapper.UserPersistenceMapperImpl;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

class UserRepositoryAdapterTest {

  private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");

  private final SpringDataUserRepository rows = mock(SpringDataUserRepository.class);
  private final UserRepositoryAdapter adapter =
      new UserRepositoryAdapter(rows, new UserPersistenceMapperImpl());

  @Test
  void readsTheRowAsAUser() {
    UserEntity row = memberRow();
    when(rows.findById(row.getId())).thenReturn(Optional.of(row));

    assertThat(adapter.findById(row.getId()))
        .get()
        .usingRecursiveComparison()
        .isEqualTo(
            User.restore(row.getId(), row.getOrganizationId(), Role.ORGANIZATION_MEMBER, true));
  }

  @Test
  void readsTheRowAsAnAccountByIdEmailOrRole() {
    UserEntity row = memberRow();
    when(rows.findById(row.getId())).thenReturn(Optional.of(row));
    when(rows.findByEmail("rita@ribeira.pt")).thenReturn(Optional.of(row));
    when(rows.findByRoleOrderByNameAsc(Role.ORGANIZATION_MEMBER)).thenReturn(List.of(row));

    UserAccount expected =
        UserAccount.restore(
            row.getId(),
            row.getOrganizationId(),
            Role.ORGANIZATION_MEMBER,
            "Rita Ferreira",
            new Email("rita@ribeira.pt"),
            "{bcrypt}hash",
            true,
            true,
            NOW.plusSeconds(3600),
            NOW.minusSeconds(60),
            NOW.minusSeconds(3600));
    assertThat(adapter.findAccountById(row.getId()))
        .get()
        .usingRecursiveComparison()
        .isEqualTo(expected);
    assertThat(adapter.findAccountByEmail(new Email("rita@ribeira.pt")))
        .get()
        .usingRecursiveComparison()
        .isEqualTo(expected);
    assertThat(adapter.findMembers())
        .singleElement()
        .usingRecursiveComparison()
        .isEqualTo(expected);
  }

  @Test
  void storesAnAccountAndRefusesATakenEmail() {
    UserAccount account =
        UserAccount.registerParticipant(new Email("ana@crowded.test"), "{bcrypt}hash", NOW);

    adapter.add(account);
    account.deactivate();
    adapter.update(account);

    ArgumentCaptor<UserEntity> added = ArgumentCaptor.forClass(UserEntity.class);
    verify(rows).saveAndFlush(added.capture());
    assertThat(added.getValue().getEmail()).isEqualTo("ana@crowded.test");
    assertThat(added.getValue().getPasswordHash()).isEqualTo("{bcrypt}hash");
    assertThat(added.getValue().isActive()).isTrue();
    ArgumentCaptor<UserEntity> updated = ArgumentCaptor.forClass(UserEntity.class);
    verify(rows).save(updated.capture());
    assertThat(updated.getValue().isActive()).isFalse();

    when(rows.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));
    assertThatThrownBy(() -> adapter.add(account))
        .isInstanceOfSatisfying(
            ConflictException.class,
            e -> assertThat(e.getCode()).isEqualTo("EMAIL_ALREADY_REGISTERED"));
  }

  @Test
  void storesOnlyTheLastSignIn() {
    UserAccount account =
        UserAccount.registerParticipant(new Email("ana@crowded.test"), "{bcrypt}hash", NOW);
    account.signIn(true, NOW.plusSeconds(60));

    adapter.saveSignIn(account);

    verify(rows).updateLastSignedInAt(account.getId(), NOW.plusSeconds(60));
    verify(rows, never()).save(any());
  }

  private static UserEntity memberRow() {
    UserEntity row = new UserEntity();
    row.setId(UUID.randomUUID());
    row.setOrganizationId(UUID.randomUUID());
    row.setRole(Role.ORGANIZATION_MEMBER);
    row.setName("Rita Ferreira");
    row.setEmail("rita@ribeira.pt");
    row.setPasswordHash("{bcrypt}hash");
    row.setActive(true);
    row.setMustChangePassword(true);
    row.setTemporaryPasswordExpiresAt(NOW.plusSeconds(3600));
    row.setLastSignedInAt(NOW.minusSeconds(60));
    row.setCreatedAt(NOW.minusSeconds(3600));
    return row;
  }
}
