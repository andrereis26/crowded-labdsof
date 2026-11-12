package com.crowded.core.organization.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crowded.core.organization.domain.model.Organization;
import com.crowded.core.organization.infrastructure.persistence.entity.OrganizationEntity;
import com.crowded.core.organization.infrastructure.persistence.mapper.OrganizationPersistenceMapperImpl;
import com.crowded.core.shared.domain.exception.ConflictException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

class OrganizationRepositoryAdapterTest {

  @Test
  void readsTheRowAsAnOrganization() {
    SpringDataOrganizationRepository rows = mock(SpringDataOrganizationRepository.class);
    OrganizationEntity row = new OrganizationEntity();
    row.setId(UUID.randomUUID());
    row.setName("Ribeira Live");
    row.setCreatedAt(Instant.parse("2026-11-01T10:00:00Z"));
    when(rows.findById(row.getId())).thenReturn(Optional.of(row));

    assertThat(
            new OrganizationRepositoryAdapter(rows, new OrganizationPersistenceMapperImpl())
                .findById(row.getId()))
        .get()
        .usingRecursiveComparison()
        .isEqualTo(
            Organization.restore(
                row.getId(), "Ribeira Live", null, Instant.parse("2026-11-01T10:00:00Z")));
  }

  @Test
  void listsByNameAndRefusesATakenName() {
    SpringDataOrganizationRepository rows = mock(SpringDataOrganizationRepository.class);
    OrganizationRepositoryAdapter adapter =
        new OrganizationRepositoryAdapter(rows, new OrganizationPersistenceMapperImpl());
    Organization ribeira =
        Organization.restore(
            UUID.randomUUID(),
            "Ribeira Live",
            UUID.randomUUID(),
            Instant.parse("2026-11-01T10:00:00Z"));
    OrganizationEntity row = new OrganizationEntity();
    row.setId(ribeira.getId());
    row.setName(ribeira.getName());
    row.setCreatedByUserId(ribeira.getCreatedByUserId());
    row.setCreatedAt(ribeira.getCreatedAt());
    when(rows.findAllByOrderByNameAsc()).thenReturn(List.of(row));

    assertThat(adapter.findAll()).singleElement().usingRecursiveComparison().isEqualTo(ribeira);

    adapter.add(ribeira);
    ArgumentCaptor<OrganizationEntity> stored = ArgumentCaptor.forClass(OrganizationEntity.class);
    verify(rows).saveAndFlush(stored.capture());
    assertThat(stored.getValue().getCreatedByUserId()).isEqualTo(ribeira.getCreatedByUserId());

    when(rows.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));
    assertThatThrownBy(() -> adapter.add(ribeira))
        .isInstanceOfSatisfying(
            ConflictException.class,
            e -> assertThat(e.getCode()).isEqualTo("ORGANIZATION_NAME_TAKEN"));
  }
}
