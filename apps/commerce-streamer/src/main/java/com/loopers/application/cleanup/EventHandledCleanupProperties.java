package com.loopers.application.cleanup;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "collector.event-handled-cleanup")
public record EventHandledCleanupProperties(
        boolean enabled,
        long fixedDelayMs,
        int retentionDays,
        int batchSize,
        /** 한 번의 스케줄 실행에서 DELETE 루프 최대 횟수 (스케줄 겹침·장시간 점유 방지). */
        int maxLoopsPerRun,
        /** 한 번의 스케줄 실행에서 삭제할 총 행 상한 (무한 루프·부하 방지). */
        long maxRowsPerRun
) {
    public EventHandledCleanupProperties {
        if (retentionDays < 1) {
            throw new IllegalArgumentException("retentionDays must be >= 1");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be >= 1");
        }
        if (maxLoopsPerRun < 1) {
            throw new IllegalArgumentException("maxLoopsPerRun must be >= 1");
        }
        if (maxRowsPerRun < 1) {
            throw new IllegalArgumentException("maxRowsPerRun must be >= 1");
        }
    }
}
