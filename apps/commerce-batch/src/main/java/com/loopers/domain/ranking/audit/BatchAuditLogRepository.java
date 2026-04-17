package com.loopers.domain.ranking.audit;

import java.time.LocalDate;
import java.util.List;

public interface BatchAuditLogRepository {

    // Command
    BatchAuditLog save(BatchAuditLog log);

    // Query
    List<BatchAuditLog> findByAnchorDate(LocalDate anchorDate);
}
