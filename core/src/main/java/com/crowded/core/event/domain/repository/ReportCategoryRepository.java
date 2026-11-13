package com.crowded.core.event.domain.repository;

import com.crowded.core.event.domain.model.ReportCategory;
import java.util.List;

/** The predefined report categories. */
public interface ReportCategoryRepository {

  /** Every category. */
  List<ReportCategory> findAll();
}
