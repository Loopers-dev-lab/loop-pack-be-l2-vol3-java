package com.loopers.domain.queue;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 대기열 관련 설정값을 중앙 관리하는 클래스.
 *
 * <p>기존 프로젝트의 {@code @Value} 패턴을 유지하되, 대기열 설정이 다수이므로
 * 하나의 클래스에 모아 관리한다. 처리량 계산, 대기 시간 추정, 수용 판단 등
 * 설정값 기반 비즈니스 로직을 캡슐화한다.</p>
 */
@Getter
@Component
public class QueueProperties {

    /** 입장 토큰 TTL (초) — 기본 5분 */
    @Value("${queue.entry-token.ttl-seconds:300}")
    private int tokenTtlSeconds;

    /** 스케줄러 실행 주기 (밀리초) — 기본 100ms */
    @Value("${queue.scheduler.interval-ms:100}")
    private int schedulerIntervalMs;

    /** 스케줄러 배치 크기 — 기본 14명 */
    @Value("${queue.scheduler.batch-size:14}")
    private int schedulerBatchSize;

    /** 대기열 최대 인원 (0이면 무제한) — 기본 무제한 */
    @Value("${queue.max-size:0}")
    private int maxSize;

    /** 대기열 활성화 여부 — 기본 true */
    @Value("${queue.enabled:true}")
    private boolean enabled;

    /**
     * 초당 처리량 계산.
     * 예: batchSize=14, intervalMs=100 → 14 × (1000/100) = 140 TPS
     */
    public double getThroughputPerSecond() {
        return (double) schedulerBatchSize * (1000.0 / schedulerIntervalMs);
    }

    /**
     * 현재 대기 순번으로 예상 대기 시간을 계산한다.
     *
     * @param position 대기 순번 (0-based ZRANK)
     * @return 예상 대기 시간 (초), 올림 처리
     */
    public int calculateEstimatedWaitSeconds(long position) {
        if (position <= 0) return 0;
        return (int) Math.ceil(position / getThroughputPerSecond());
    }

    /**
     * 현재 대기열 크기로 추가 수용 가능 여부를 판단한다.
     *
     * @param currentSize 현재 대기 인원 (ZCARD)
     * @return maxSize=0(무제한)이면 항상 true, 아니면 currentSize < maxSize
     */
    public boolean canAccept(long currentSize) {
        return maxSize <= 0 || currentSize < maxSize;
    }
}
