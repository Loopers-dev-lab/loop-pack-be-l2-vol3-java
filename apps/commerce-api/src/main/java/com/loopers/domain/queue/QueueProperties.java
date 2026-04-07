package com.loopers.domain.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 대기열 설정 속성
 *
 * 배치 크기 산정 근거:
 * - DB 커넥션 풀 10개, 주문 평균 처리 시간 200ms → TPS 50
 * - 안전 마진 60% 적용 → 실효 TPS 30
 * - 스케줄러 주기 1초 → 배치 크기 30
 *
 * 토큰 TTL 근거:
 * - 사용자 평균 주문 체류 시간 ~35초
 * - 여유 포함 → Access TTL 180초(3분)
 * - Hard Limit 600초(10분)
 */
@ConfigurationProperties(prefix = "queue")
public class QueueProperties {

    private boolean enabled = true;
    private int batchSize = 30;
    private long schedulerIntervalMs = 1000;
    private int tokenTtlSeconds = 180;
    private int maxWaitingSize = 100000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getSchedulerIntervalMs() {
        return schedulerIntervalMs;
    }

    public void setSchedulerIntervalMs(long schedulerIntervalMs) {
        this.schedulerIntervalMs = schedulerIntervalMs;
    }

    public int getTokenTtlSeconds() {
        return tokenTtlSeconds;
    }

    public void setTokenTtlSeconds(int tokenTtlSeconds) {
        this.tokenTtlSeconds = tokenTtlSeconds;
    }

    public int getMaxWaitingSize() {
        return maxWaitingSize;
    }

    public void setMaxWaitingSize(int maxWaitingSize) {
        this.maxWaitingSize = maxWaitingSize;
    }
}
