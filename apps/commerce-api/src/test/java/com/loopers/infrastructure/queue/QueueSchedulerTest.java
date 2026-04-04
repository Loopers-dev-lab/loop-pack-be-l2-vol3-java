package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.EntryToken;
import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.QueueStatus;
import com.loopers.domain.queue.WaitingQueueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class QueueSchedulerTest {

    @Autowired
    private WaitingQueueRepository waitingQueueRepository;

    @Autowired
    private EntryTokenRepository entryTokenRepository;

    @Value("${queue.max-slot}")
    private int maxSlot;

    @Value("${queue.batch-size}")
    private int batchSize;

    @Value("${queue.scheduler-lock-ttl-ms}")
    private long schedulerLockTtlMs;

    @Value("${queue.jitter-range-ms}")
    private long jitterRangeMs;

    @Value("${queue.token-ttl-seconds}")
    private long tokenTtlSeconds;

    @Value("${queue.status-ttl-seconds}")
    private long statusTtlSeconds;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private QueueScheduler queueScheduler;

    @BeforeEach
    void setUp() {
        redisTemplate.delete("waiting-queue");
        Set<String> keys = redisTemplate.keys("entry-token:*");
        if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
        keys = redisTemplate.keys("queue-status:*");
        if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
        redisTemplate.delete("lock:scheduler");
        redisTemplate.delete("staging:batch");

        queueScheduler = new QueueScheduler(
            waitingQueueRepository, entryTokenRepository,
            maxSlot, batchSize, schedulerLockTtlMs, jitterRangeMs,
            tokenTtlSeconds, statusTtlSeconds
        );
    }

    @DisplayName("스케줄러가 실행될 때, ")
    @Nested
    class ProcessQueue {

        @DisplayName("대기열에서 배치 크기만큼 꺼내 토큰을 발급한다.")
        @Test
        void issuesTokens_forBatchSizeUsers() {
            for (int i = 1; i <= 20; i++) {
                waitingQueueRepository.add((long) i, 1000.0 + i);
            }

            queueScheduler.processQueue();

            for (int i = 1; i <= batchSize; i++) {
                Optional<EntryToken> token = entryTokenRepository.findByUserId((long) i);
                assertThat(token).isPresent();

                Optional<String> status = entryTokenRepository.getStatus((long) i);
                assertThat(status).isPresent();
                assertThat(status.get()).isEqualTo(QueueStatus.TOKEN_ISSUED.name());
            }

            assertThat(waitingQueueRepository.size()).isEqualTo(20 - batchSize);
        }

        @DisplayName("대기열이 비어있으면, 아무 동작도 하지 않는다.")
        @Test
        void doesNothing_whenQueueIsEmpty() {
            queueScheduler.processQueue();

            assertThat(entryTokenRepository.countActiveTokens()).isZero();
        }

        @DisplayName("활성 토큰이 maxSlot 이상이면, 추가 발급하지 않는다.")
        @Test
        void doesNotIssue_whenMaxSlotReached() {
            for (int i = 1; i <= 5; i++) {
                waitingQueueRepository.add((long) i, 1000.0 + i);
            }
            for (int i = 100; i < 100 + maxSlot; i++) {
                entryTokenRepository.save(
                    new EntryToken((long) i, "token-" + i, System.currentTimeMillis()),
                    tokenTtlSeconds
                );
            }

            queueScheduler.processQueue();

            assertThat(waitingQueueRepository.size()).isEqualTo(5);
        }

        @DisplayName("실행 완료 후 즉시 재실행할 수 있다.")
        @Test
        void canRerunImmediately_afterCompletion() {
            for (int i = 1; i <= 40; i++) {
                waitingQueueRepository.add((long) i, 1000.0 + i);
            }

            queueScheduler.processQueue();
            queueScheduler.processQueue();

            assertThat(waitingQueueRepository.size()).isEqualTo(40 - batchSize * 2);
        }

        @DisplayName("staging에 고아 사용자가 있으면, 대기열에 복구한다.")
        @Test
        void recoversOrphanedUsers_fromStagingBatch() {
            // maxSlot만큼 토큰을 채워서 추가 발급을 막는다
            for (int i = 100; i < 100 + maxSlot; i++) {
                entryTokenRepository.save(
                    new EntryToken((long) i, "token-" + i, System.currentTimeMillis()),
                    tokenTtlSeconds
                );
            }
            // user 2는 이미 토큰이 있으므로 복구 대상이 아님
            entryTokenRepository.saveStagingBatch(List.of(1L, 2L, 3L));
            entryTokenRepository.save(
                new EntryToken(2L, "token-2", System.currentTimeMillis()),
                tokenTtlSeconds
            );

            queueScheduler.processQueue();

            // user 1, 3은 토큰 없이 staging에만 있었으므로 대기열로 복구
            assertThat(waitingQueueRepository.rank(1L)).isNotNull();
            assertThat(waitingQueueRepository.rank(3L)).isNotNull();
            // user 2는 이미 토큰이 있으므로 대기열에 복구하지 않음
            assertThat(waitingQueueRepository.rank(2L)).isNull();
        }
    }
}
