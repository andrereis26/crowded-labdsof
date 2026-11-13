package com.crowded.core.event.infrastructure.persistence.repository;

import com.crowded.core.event.domain.model.ReportCategory;
import com.crowded.core.event.domain.repository.ReportCategoryRepository;
import com.crowded.core.event.infrastructure.persistence.mapper.EventPersistenceMapper;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/** {@link ReportCategoryRepository} on PostgreSQL. */
@Repository
class ReportCategoryRepositoryAdapter implements ReportCategoryRepository {

  private final SpringDataReportCategoryRepository categories;
  private final EventPersistenceMapper mapper;

  ReportCategoryRepositoryAdapter(
      SpringDataReportCategoryRepository categories, EventPersistenceMapper mapper) {
    this.categories = categories;
    this.mapper = mapper;
  }

  @Override
  public List<ReportCategory> findAll() {
    return categories.findAll(Sort.by("code")).stream().map(mapper::toDomain).toList();
  }
}
