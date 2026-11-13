package com.crowded.core.event.infrastructure.persistence.repository;

import com.crowded.core.event.infrastructure.persistence.entity.ReportCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@code report_category}. */
interface SpringDataReportCategoryRepository extends JpaRepository<ReportCategoryEntity, String> {}
