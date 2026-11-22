package com.crowded.core.event.domain.repository;

import com.crowded.core.event.domain.model.Alert;

/** The operational alerts organizations send. */
public interface AlertRepository {

  /** Stores a sent alert, in the caller's transaction. */
  void save(Alert alert);
}
