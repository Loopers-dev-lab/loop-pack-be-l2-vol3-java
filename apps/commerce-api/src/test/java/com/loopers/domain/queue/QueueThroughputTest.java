package com.loopers.domain.queue;

import com.loopers.application.queue.EntryTokenScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class QueueThroughputTest {

    @MockitoBean
    private EntryTokenScheduler entryTokenScheduler;

    @Autowired
    private QueueService queueService;

    @Autowired
    private EntryTokenService entryTokenService;

    @Autowired
    private QueueProperties queueProperties;

    @Autowired
    @Qualifier("redisTemplateMaster")
    private RedisTemplate<String, String> masterRedisTemplate;

    @BeforeEach
    void setUp() {
        masterRedisTemplate.delete("queue:waiting");
        Set<String> tokenKeys = masterRedisTemplate.keys("entry-token:*");
        if (tokenKeys != null && !tokenKeys.isEmpty()) {
            masterRedisTemplate.delete(tokenKeys);
        }
    }

    @Nested
    @DisplayName("처리량 초과 테스트")
    class ThroughputOverflow {

        @DisplayName("popUsers는 배치 크기만큼만 꺼내고 나머지는 대기열에 유지한다")
        @Test
        void onlyBatchSizeProcessedPerCycle() {
            // given — 배치 크기의 3배 이상 유저 진입
            int totalUsers = queueProperties.batchSize() * 3 + 8;
            for (int i = 1; i <= totalUsers; i++) {
                queueService.enter((long) i);
            }
            long initialSize = queueService.getQueueSize();

            // when — 1회 팝
            Set<ZSetOperations.TypedTuple<String>> popped =
                    queueService.popUsers(queueProperties.batchSize());

            // then — 정확히 배치 크기만큼 꺼냄
            assertThat(popped).hasSize(queueProperties.batchSize());

            // 꺼낸 유저에게 토큰 발급
            popped.forEach(user -> {
                if (user.getValue() != null) {
                    entryTokenService.issueToken(Long.valueOf(user.getValue()));
                }
            });

            // 대기열 크기가 배치 크기만큼 줄었는지 확인
            long afterSize = queueService.getQueueSize();
            assertThat(initialSize - afterSize).isGreaterThanOrEqualTo(queueProperties.batchSize());
        }

        @DisplayName("여러 번 스케줄러를 돌리면 모든 유저가 토큰을 발급받는다")
        @Test
        void allUsersEventuallyGetTokens() {
            // given — 50명 진입
            int totalUsers = 50;
            for (int i = 1; i <= totalUsers; i++) {
                queueService.enter((long) i);
            }

            // when — 대기열이 빌 때까지 스케줄러 반복 실행
            int cycles = 0;
            int maxCycles = totalUsers; // 무한 루프 방지
            while (queueService.getQueueSize() > 0 && cycles < maxCycles) {
                var popped = queueService.popUsers(queueProperties.batchSize());
                popped.forEach(user -> {
                    if (user.getValue() != null) {
                        entryTokenService.issueToken(Long.valueOf(user.getValue()));
                    }
                });
                cycles++;
            }

            // then — 모든 유저에게 토큰 발급 완료
            for (int i = 1; i <= totalUsers; i++) {
                assertThat(entryTokenService.getToken((long) i))
                        .as("userId=%d 토큰 발급 확인", i)
                        .isNotNull();
            }
            assertThat(queueService.getQueueSize()).isZero();
        }

        @DisplayName("배치 크기 초과 동시 진입 시에도 대기열과 토큰 발급이 안정적이다")
        @Test
        void concurrentOverflowRemainsStable() throws InterruptedException {
            // given — 200명 동시 진입
            int totalUsers = 200;
            ExecutorService executor = Executors.newFixedThreadPool(50);
            CountDownLatch latch = new CountDownLatch(totalUsers);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < totalUsers; i++) {
                long userId = i + 1;
                executor.submit(() -> {
                    try {
                        queueService.enter(userId);
                        successCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executor.shutdown();

            // then — 전원 진입 성공
            assertThat(successCount.get()).isEqualTo(totalUsers);

            // when — 대기열이 빌 때까지 배치 처리 반복
            int maxCycles = totalUsers;
            int cycles = 0;
            while (queueService.getQueueSize() > 0 && cycles < maxCycles) {
                var popped = queueService.popUsers(queueProperties.batchSize());
                for (var user : popped) {
                    if (user.getValue() != null) {
                        entryTokenService.issueToken(Long.valueOf(user.getValue()));
                    }
                }
                cycles++;
            }

            // then — 대기열 완전 소진, 전원 토큰 발급 완료
            assertThat(queueService.getQueueSize()).isZero();
            for (int i = 1; i <= totalUsers; i++) {
                assertThat(entryTokenService.getToken((long) i))
                        .as("userId=%d 토큰 발급 확인", i)
                        .isNotNull();
            }
        }
    }
}
