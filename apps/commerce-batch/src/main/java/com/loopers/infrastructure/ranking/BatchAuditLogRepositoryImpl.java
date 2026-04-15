package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.audit.BatchAuditLog;
import com.loopers.domain.ranking.audit.BatchAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class BatchAuditLogRepositoryImpl implements BatchAuditLogRepository {

    private final BatchAuditLogJpaRepository jpaRepository;

    @Override
    public BatchAuditLog save(BatchAuditLog log) {
        return jpaRepository.save(log);
    }

    @Override
    public List<BatchAuditLog> findByAnchorDate(LocalDate anchorDate) {
        return jpaRepository.findByAnchorDate(anchorDate);
    }
}
