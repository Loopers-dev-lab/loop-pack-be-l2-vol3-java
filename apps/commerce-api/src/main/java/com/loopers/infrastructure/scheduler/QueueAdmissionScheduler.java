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
 * <p>100ms 주기로 대기열 앞에서 14명을 꺼내 입장 토큰을 발급한다.
 * 초당 140명 입장 = HikariCP 40풀의 70% 활용률.</p>
 *
 * <p>산술 근거: 14명/배치 x 10회/초 = 140 TPS,
 * 140 x 0.2s = 28 동시 커넥션 (풀 40의 70%)</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueAdmissionScheduler {

    private static final int BATCH_SIZE = 14;

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
}
