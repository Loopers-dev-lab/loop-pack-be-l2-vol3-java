package com.loopers.batch.scheduler;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Batch Scheduler 설정 프로퍼티.
 *
 * @param enabled 스케줄러 활성화 여부
 * @param weeklyCron 주간 랭킹 Job 실행 cron (기본: 매주 월요일 새벽 2시)
 * @param monthlyCron 월간 랭킹 Job 실행 cron (기본: 매월 1일 새벽 3시)
 */
@ConfigurationProperties(prefix = "batch.scheduler")
public record BatchSchedulerProperties(
    boolean enabled,
    String weeklyCron,
    String monthlyCron
) {
    public BatchSchedulerProperties {
        if (weeklyCron == null) {
            weeklyCron = "0 0 2 ? * MON";
        }
        if (monthlyCron == null) {
            monthlyCron = "0 0 3 1 * ?";
        }
    }
}