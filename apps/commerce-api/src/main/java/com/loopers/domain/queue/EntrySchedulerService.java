package com.loopers.domain.queue;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 대기열에서 일정 인원을 꺼내 입장 토큰을 발급하는 스케줄러용 도메인 서비스.
 * <p>
 * 다중 인스턴스 환경에서는 {@link SchedulerLockRepository}로 한 번에 하나의 틱만 실행하고,
 * {@link WaitingQueueRepository#popOldest}로 이벤트별 FIFO(점수 오름차순) 출구,
 * {@link EntryTokenRepository}에 TTL이 있는 입장 자격을 남긴다.
 */
@Component
public class EntrySchedulerService {

    private final WaitingQueueRepository waitingQueueRepository;
    private final EntryTokenRepository entryTokenRepository;
    private final SchedulerLockRepository schedulerLockRepository;
    private final EntryTokenGenerator entryTokenGenerator;
    private final JitterDelay jitterDelay;
    private final EntrySchedulerLockObservation lockObservation;

    public EntrySchedulerService(
        WaitingQueueRepository waitingQueueRepository,
        EntryTokenRepository entryTokenRepository,
        SchedulerLockRepository schedulerLockRepository,
        EntryTokenGenerator entryTokenGenerator,
        JitterDelay jitterDelay,
        EntrySchedulerLockObservation lockObservation
    ) {
        this.waitingQueueRepository = waitingQueueRepository;
        this.entryTokenRepository = entryTokenRepository;
        this.schedulerLockRepository = schedulerLockRepository;
        this.entryTokenGenerator = entryTokenGenerator;
        this.jitterDelay = jitterDelay;
        this.lockObservation = lockObservation;
    }

    /**
     * 한 틱(tick) 동안: 분산 락 획득 → 대기열에서 최대 {@code maxBatchSize}명 pop → 토큰 저장 → 하트비트 갱신.
     *
     * @param eventId            대기열(이벤트) 식별자
     * @param maxBatchSize       이번 틱에서 입장시킬 최대 인원
     * @param tokenTtlSeconds    입장 토큰(자격) TTL
     * @param lockTtlSeconds     스케줄러 락 TTL (장애 시 락이 풀리도록 상한)
     * @param lockKey            분산 락 Redis 키
     * @param heartbeatKey       스케줄러 생존·마지막 성공 틱 표시용 키
     * @param heartbeatTtlSeconds 하트비트 값 TTL
     * @return 락 획득 여부와 실제 pop·토큰 발급된 인원 수
     */
    public ReleaseResult releaseEntries(
        String eventId,
        int maxBatchSize,
        long tokenTtlSeconds,
        long lockTtlSeconds,
        String lockKey,
        String heartbeatKey,
        long heartbeatTtlSeconds
    ) {
        String lockValue = UUID.randomUUID().toString();
        // 분산 락: 동시에 여러 노드가 pop/토큰 발급을 하지 않도록 직렬화
        boolean lockAcquired = schedulerLockRepository.tryAcquireLock(lockKey, lockValue, lockTtlSeconds);
        if (!lockAcquired) {
            lockObservation.onLockNotAcquired();
            return new ReleaseResult(false, 0);
        }

        List<Long> userIds = waitingQueueRepository.popOldest(eventId, maxBatchSize);
        for (Long userId : userIds) {
            // 0~300ms 무작위 지연: 동일 틱에 토큰 저장이 몰릴 때 Redis/네트워크 스파이크 완화
            long jitterMillis = ThreadLocalRandom.current().nextLong(0, 301);
            jitterDelay.delay(jitterMillis);
            String token = entryTokenGenerator.generate();
            entryTokenRepository.saveEntryToken(userId, token, tokenTtlSeconds);
        }
        // 운영·모니터링에서 "마지막으로 스케줄러 틱이 성공한 시각" 확인용
        schedulerLockRepository.updateHeartbeat(heartbeatKey, String.valueOf(System.currentTimeMillis()), heartbeatTtlSeconds);

        return new ReleaseResult(true, userIds.size());
    }

    /**
     * @param lockAcquired   이번 호출에서 분산 락을 잡았는지 (false면 대기열/토큰 작업 없음)
     * @param releasedCount  실제 pop 후 토큰까지 발급된 사용자 수
     */
    public record ReleaseResult(
        boolean lockAcquired,
        int releasedCount
    ) {
    }
}
