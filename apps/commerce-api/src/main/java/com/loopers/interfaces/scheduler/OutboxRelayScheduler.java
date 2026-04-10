package com.loopers.interfaces.scheduler;

import com.loopers.application.outbox.OutboxRelayProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Outbox 보완 Relay — 즉시 발행(afterCommit)이 실패한 이벤트만 수거.
 *
 * 메인 발행: OutboxEventService의 afterCommit 비동기 send (99.x%)
 * 보완 발행: 이 스케줄러가 stale PENDING 수거 (0.x%)
 *
 * SELECT ... FOR UPDATE SKIP LOCKED로 멀티 인스턴스에서도 중복 처리 방지.
 * 트랜잭션은 OutboxRelayProcessor가 소유 (lock 범위 = 조회 + 처리 + 상태 변경).
 */
@Slf4j
@Component
@EnableScheduling
@ConditionalOnProperty(name = "scheduler.outbox.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private static final int BATCH_SIZE = 200;

    private final OutboxRelayProcessor outboxRelayProcessor;

    @Scheduled(fixedDelay = 60000)
    public void compensatePendingEvents() {
        outboxRelayProcessor.relayPendingEvents(BATCH_SIZE);
    }
}
