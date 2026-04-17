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
 * MV 적재 실행 이력.
 * "이 anchor, 이 period, 이 weight_group 에 N건 적재 완료" 를 기록한다.
 * Step 5 (promote) 에서 MV INSERT 와 같은 TX 안에서 커밋된다.
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

}
