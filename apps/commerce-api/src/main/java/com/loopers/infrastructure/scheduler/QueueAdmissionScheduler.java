package com.loopers.infrastructure.scheduler;

import com.loopers.infrastructure.redis.WaitingQueueRedisRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 대기열 입장 스케줄러.
 *
 * <p>100ms 주기로 대기열 앞에서 8명을 꺼내 입장 토큰을 발급한다.
 * 초당 80명 입장 = p99(358ms) 기준 28.6 커넥션 (HikariCP 풀 40의 72%).</p>
 *
 * <p>산술 근거: 8명/배치 × 10회/초 = 80 TPS,
 * 80 × 0.358s(p99) = 28.6 동시 커넥션 (풀 40의 72%)</p>
 *
 * <p>타임아웃 정리: 10초 주기로 600초(10분) 이상 대기한 엔트리를 제거한다.
 * max_queue = 80 TPS × 600초 = 48,000명.</p>
 *
 * <p>Redis 장애 대비: 에러 로그를 10초에 1회로 쓰로틀링한다.
 * admitUsers()가 100ms마다 실행되므로, 장애 시 분당 600회 예외가 발생하는데,
 * 매번 로그를 찍으면 로그 시스템에 부하가 걸리고 중요한 에러가 묻힌다.</p>
 */
@Slf4j
@Component
public class QueueAdmissionScheduler {

    private static final int BATCH_SIZE = 8;
    private static final long MAX_WAIT_SECONDS = 600;
    private static final long ERROR_LOG_INTERVAL_MILLIS = 10_000;

    private final WaitingQueueRedisRepository waitingQueueRedisRepository;

    private final Counter admissionCounter;
    private final Counter admissionErrorCounter;
    private final Counter cleanupRemovedCounter;
    private final AtomicLong waitingSize;

    private final AtomicLong lastAdmitErrorLogTime = new AtomicLong(0);
    private final AtomicLong lastCleanupErrorLogTime = new AtomicLong(0);

    public QueueAdmissionScheduler(
        WaitingQueueRedisRepository waitingQueueRedisRepository,
        MeterRegistry meterRegistry
    ) {
        this.waitingQueueRedisRepository = waitingQueueRedisRepository;

        this.admissionCounter = Counter.builder("queue.admission.count")
            .description("입장 처리된 유저 수")
            .register(meterRegistry);
        this.admissionErrorCounter = Counter.builder("queue.admission.errors")
            .description("Redis 장애 횟수")
            .register(meterRegistry);
        this.cleanupRemovedCounter = Counter.builder("queue.cleanup.removed")
            .description("타임아웃 정리된 유저 수")
            .register(meterRegistry);
        this.waitingSize = new AtomicLong(0);
        meterRegistry.gauge("queue.waiting.size", waitingSize);
    }

    @Scheduled(fixedRate = 100)
    public void admitUsers() {
        try {
            List<String> admitted = waitingQueueRedisRepository.popMinAndIssueTokens(BATCH_SIZE);
            if (admitted.isEmpty()) {
                return;
            }
            admissionCounter.increment(admitted.size());
            log.debug("대기열 입장 처리: {}명", admitted.size());
        } catch (Exception e) {
            admissionErrorCounter.increment();
            throttledWarn(lastAdmitErrorLogTime, "입장 처리", e);
        } finally {
            try {
                waitingSize.set(waitingQueueRedisRepository.size());
            } catch (Exception ignored) {
                // 대기열 크기 조회 실패는 무시 (메트릭 갱신 실패일 뿐)
            }
        }
    }

    @Scheduled(fixedRate = 10_000)
    public void removeExpiredEntries() {
        try {
            long cutoff = System.currentTimeMillis() - (MAX_WAIT_SECONDS * 1000);
            long removed = waitingQueueRedisRepository.removeExpiredEntries(cutoff);
            if (removed > 0) {
                cleanupRemovedCounter.increment(removed);
                log.info("대기열 타임아웃 정리: {}명 제거", removed);
            }
        } catch (Exception e) {
            throttledWarn(lastCleanupErrorLogTime, "타임아웃 정리", e);
        }
    }

    private void throttledWarn(AtomicLong lastLogTime, String operation, Exception e) {
        long now = System.currentTimeMillis();
        long last = lastLogTime.get();
        if (now - last >= ERROR_LOG_INTERVAL_MILLIS && lastLogTime.compareAndSet(last, now)) {
            log.warn("대기열 {} Redis 장애: {}", operation, e.getMessage());
        }
    }
}
