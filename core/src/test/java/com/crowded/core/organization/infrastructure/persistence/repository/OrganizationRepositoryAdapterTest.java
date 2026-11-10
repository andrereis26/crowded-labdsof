package com.crowded.core.organization.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crowded.core.organization.domain.model.Organization;
import com.crowded.core.organization.infrastructure.persistence.entity.OrganizationEntity;
import com.crowded.core.organization.infrastructure.persistence.mapper.OrganizationPersistenceMapperImpl;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrganizationRepositoryAdapterTest {

  @Test
  void readsTheRowAsAnOrganization() {
    SpringDataOrganizationRepository rows = mock(SpringDataOrganizationRepository.class);
    OrganizationEntity row = new OrganizationEntity();
    row.setId(UUID.randomUUID());
    row.setName("Ribeira Live");
    when(rows.findById(row.getId())).thenReturn(Optional.of(row));

    assertThat(
            new OrganizationRepositoryAdapter(rows, new OrganizationPersistenceMapperImpl())
                .findById(row.getId()))
        .contains(new Organization(row.getId(), "Ribeira Live"));
  }
}
