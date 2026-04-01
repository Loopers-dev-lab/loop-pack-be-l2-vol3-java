package com.loopers.domain.queue;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 대기열 → 입장 토큰 발급 스케줄러.
 *
 * <p>100ms마다 ZPOPMIN으로 대기열 앞 N명을 꺼내고 각각에게 토큰을 발급한다.
 * 다중 인스턴스 환경에서 이중 실행을 방지하기 위해 Redis SETNX 리더 선출을 사용한다.</p>
 *
 * <p>처리량: batchSize(14) × 10회/초 = 140 TPS</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "queue.scheduler.enabled", havingValue = "true")
public class OrderQueueScheduler {

    private final QueueService queueService;
    private final QueueRepository queueRepository;
    private final EntryTokenService entryTokenService;
    private final QueueProperties queueProperties;
    private final RedisTemplate<String, String> redisTemplateMaster;

    private static final String LOCK_KEY = "queue:scheduler:lock";
    private final String instanceId = UUID.randomUUID().toString();

    public OrderQueueScheduler(
            QueueService queueService,
            QueueRepository queueRepository,
            EntryTokenService entryTokenService,
            QueueProperties queueProperties,
            @Qualifier("redisTemplateMaster") RedisTemplate<String, String> redisTemplateMaster) {
        this.queueService = queueService;
        this.queueRepository = queueRepository;
        this.entryTokenService = entryTokenService;
        this.queueProperties = queueProperties;
        this.redisTemplateMaster = redisTemplateMaster;
    }

    /**
     * 100ms 주기로 대기열을 처리한다.
     */
    @Scheduled(fixedRateString = "${queue.scheduler.interval-ms:100}",
               scheduler = "queueTaskScheduler")
    public void processQueue() {
        // 1. Redis SETNX 리더 선출
        if (!acquireLock()) {
            return;
        }

        // 2. ZPOPMIN으로 배치 추출
        int batchSize = queueProperties.getSchedulerBatchSize();
        List<QueueEntry> entries = queueService.popBatch(batchSize);

        if (entries.isEmpty()) {
            return;
        }

        // 3. 각각 토큰 발급 + 실패 보상
        int successCount = 0;
        for (QueueEntry entry : entries) {
            try {
                entryTokenService.issueToken(entry.userId());
                successCount++;
            } catch (Exception e) {
                log.warn("토큰 발급 실패, 대기열 복귀: userId={}", entry.userId(), e);
                compensateFailure(entry);
            }
        }

        if (successCount > 0) {
            log.debug("토큰 발급 완료: {}명", successCount);
        }
    }

    private boolean acquireLock() {
        try {
            Boolean acquired = redisTemplateMaster.opsForValue()
                    .setIfAbsent(LOCK_KEY, instanceId, Duration.ofMillis(150));
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            log.warn("스케줄러 리더 선출 실패 (Redis 장애), 이번 주기 skip: {}", e.getMessage());
            return false;
        }
    }

    private void compensateFailure(QueueEntry entry) {
        try {
            queueRepository.addIfAbsent(entry.userId(), entry.score());
            log.info("대기열 복귀 완료: userId={}, score={}", entry.userId(), entry.score());
        } catch (Exception e) {
            log.error("대기열 복귀 실패! userId={}가 유실됨. 수동 복구 필요.", entry.userId(), e);
        }
    }

    String getInstanceId() {
        return instanceId;
    }
}
