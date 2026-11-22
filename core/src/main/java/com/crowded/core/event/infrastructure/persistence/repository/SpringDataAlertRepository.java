package com.crowded.core.event.infrastructure.persistence.repository;

import com.crowded.core.event.infrastructure.persistence.entity.AlertEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@code alert}. */
interface SpringDataAlertRepository extends JpaRepository<AlertEntity, UUID> {}
