package com.loopers.infrastructure.scheduler;

import com.loopers.infrastructure.redis.EntryTokenRedisRepository;
import com.loopers.infrastructure.redis.WaitingQueueRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueAdmissionScheduler {

    private static final int BATCH_SIZE = 8;
    private static final long MAX_WAIT_SECONDS = 600;

    private final WaitingQueueRedisRepository waitingQueueRedisRepository;
    private final EntryTokenRedisRepository entryTokenRedisRepository;

    @Scheduled(fixedRate = 100)
    public void admitUsers() {
        Set<TypedTuple<String>> admitted = waitingQueueRedisRepository.popMin(BATCH_SIZE);
        if (admitted.isEmpty()) {
            return;
        }

        for (TypedTuple<String> tuple : admitted) {
            entryTokenRedisRepository.issue(Long.parseLong(tuple.getValue()));
        }

        log.debug("대기열 입장 처리: {}명", admitted.size());
    }

    @Scheduled(fixedRate = 10_000)
    public void removeExpiredEntries() {
        long cutoff = System.currentTimeMillis() - (MAX_WAIT_SECONDS * 1000);
        long removed = waitingQueueRedisRepository.removeExpiredEntries(cutoff);
        if (removed > 0) {
            log.info("대기열 타임아웃 정리: {}명 제거", removed);
        }
    }
}
