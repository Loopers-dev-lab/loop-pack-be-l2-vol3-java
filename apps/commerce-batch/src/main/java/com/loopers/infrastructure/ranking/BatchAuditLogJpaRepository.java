package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.audit.BatchAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

interface BatchAuditLogJpaRepository extends JpaRepository<BatchAuditLog, Long> {

    List<BatchAuditLog> findByAnchorDate(LocalDate anchorDate);
}
