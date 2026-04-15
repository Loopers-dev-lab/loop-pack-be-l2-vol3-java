package com.loopers.domain.ranking.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Step 7 (audit) 의 검증 결과 기록.
 * BATCH_JOB_EXECUTION 이 "Job 이 COMPLETED 되었는가" 를 보장한다면,
 * 이 테이블은 "결과 데이터 자체가 불변조건을 만족하는가" 의 비즈니스 감사 로그.
 */
@Entity
@Table(
        name = "batch_audit_log",
        indexes = @Index(
                name = "idx_audit_anchor",
                columnList = "anchor_date, period_type, weight_group"
        )
)
@Getter
public class BatchAuditLog {

    public static final String STATUS_OK = "OK";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_execution_id", nullable = false)
    private Long jobExecutionId;

    @Column(name = "anchor_date", nullable = false)
    private LocalDate anchorDate;

    @Column(name = "period_type", length = 16, nullable = false)
    private String periodType;

    @Column(name = "weight_group", length = 32, nullable = false)
    private String weightGroup;

    @Column(name = "status", length = 8, nullable = false)
    private String status;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected BatchAuditLog() {
    }

    public static BatchAuditLog ok(Long jobExecutionId, LocalDate anchorDate,
                                   String periodType, String weightGroup, int rowCount) {
        BatchAuditLog log = new BatchAuditLog();
        log.jobExecutionId = jobExecutionId;
        log.anchorDate = anchorDate;
        log.periodType = periodType;
        log.weightGroup = weightGroup;
        log.status = STATUS_OK;
        log.rowCount = rowCount;
        log.reason = null;
        log.createdAt = LocalDateTime.now();
        return log;
    }

    public static BatchAuditLog failed(Long jobExecutionId, LocalDate anchorDate,
                                       String periodType, String weightGroup,
                                       int rowCount, String reason) {
        BatchAuditLog log = new BatchAuditLog();
        log.jobExecutionId = jobExecutionId;
        log.anchorDate = anchorDate;
        log.periodType = periodType;
        log.weightGroup = weightGroup;
        log.status = STATUS_FAILED;
        log.rowCount = rowCount;
        log.reason = reason;
        log.createdAt = LocalDateTime.now();
        return log;
    }
}
