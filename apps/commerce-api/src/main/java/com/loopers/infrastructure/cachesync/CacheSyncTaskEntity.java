package com.loopers.infrastructure.cachesync;

import com.loopers.domain.AutoIncrementBaseEntity;
import com.loopers.domain.cachesync.CacheSyncAggregateType;
import com.loopers.domain.cachesync.CacheSyncOperationType;
import com.loopers.domain.cachesync.CacheSyncTask;
import com.loopers.domain.cachesync.CacheSyncTaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "read_model_sync_task")
public class CacheSyncTaskEntity extends AutoIncrementBaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "aggregate_type", nullable = false, length = 50)
    private CacheSyncAggregateType aggregateType;

    @Column(name = "aggregate_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID aggregateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 30)
    private CacheSyncOperationType operationType;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CacheSyncTaskStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private ZonedDateTime nextAttemptAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    protected CacheSyncTaskEntity() {
    }

    private CacheSyncTaskEntity(
            CacheSyncAggregateType aggregateType,
            UUID aggregateId,
            CacheSyncOperationType operationType,
            String payloadJson,
            CacheSyncTaskStatus status,
            int attemptCount,
            ZonedDateTime nextAttemptAt,
            String lastError
    ) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.operationType = operationType;
        this.payloadJson = payloadJson;
        this.status = status;
        this.attemptCount = attemptCount;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = lastError;
    }

    public static CacheSyncTaskEntity from(CacheSyncTask task) {
        return new CacheSyncTaskEntity(
                task.aggregateType(),
                task.aggregateId(),
                task.operationType(),
                task.payloadJson(),
                task.status(),
                task.attemptCount(),
                task.nextAttemptAt(),
                task.lastError()
        );
    }

    public CacheSyncTask toDomain() {
        return new CacheSyncTask(
                getId(),
                aggregateType,
                aggregateId,
                operationType,
                payloadJson,
                status,
                attemptCount,
                nextAttemptAt,
                lastError
        );
    }
}
