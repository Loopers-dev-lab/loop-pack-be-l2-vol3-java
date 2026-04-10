package com.loopers.domain.queue;

import static org.assertj.core.api.Assertions.assertThat;

import com.loopers.utils.RedisCleanUp;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
class QueueVerificationTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private TokenScheduler tokenScheduler;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("동시 진입 테스트 — ")
    @Nested
    class ConcurrentEnter {

        @DisplayName("같은 userId로 동시에 10번 진입해도 대기열에 1건만 등록된다.")
        @Test
        void registersOnce_whenSameUserEntersConcurrently() throws InterruptedException {
            // arrange
            String userId = "user-1";
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // act
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        queueService.enter(userId);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executor.shutdown();

            // assert — 대기열에 1명만 있어야 함
            assertThat(queueService.getTotalCount()).isEqualTo(1L);
        }

        @DisplayName("N명이 동시에 진입하면 각자 고유한 순번을 받는다.")
        @Test
        void eachUserGetsUniquePosition_whenNUserEnterConcurrently() throws InterruptedException {
            // arrange
            int userCount = 20;
            CountDownLatch latch = new CountDownLatch(userCount);
            ExecutorService executor = Executors.newFixedThreadPool(userCount);

            // act
            for (int i = 0; i < userCount; i++) {
                final String userId = "user-" + i;
                executor.submit(() -> {
                    try {
                        queueService.enter(userId);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executor.shutdown();

            // assert — 20명 모두 등록됨
            assertThat(queueService.getTotalCount()).isEqualTo(userCount);
        }
    }

    @DisplayName("토큰 만료 테스트 — ")
    @Nested
    class TokenExpiry {

        @DisplayName("TTL이 만료된 토큰은 유효하지 않다.")
        @Test
        void invalidatesToken_whenTtlExpires() throws InterruptedException {
            // arrange — 토큰 발급 후 TTL을 1초로 강제 설정
            String userId = "user-1";
            String token = tokenService.issue(userId);
            redisTemplate.expire("token:" + userId, 1, TimeUnit.SECONDS);

            // act — TTL 만료 대기
            TimeUnit.SECONDS.sleep(2);

            // assert — 토큰 무효화
            assertThat(tokenService.isValid(userId, token)).isFalse();
            assertThat(tokenService.findToken(userId)).isEmpty();
        }
    }

    @DisplayName("처리량 초과 테스트 — ")
    @Nested
    class Throughput {

        @DisplayName("대기열 인원이 배치 크기를 초과해도 스케줄러는 안정적으로 N명만 처리한다.")
        @Test
        void processesOnlyBatchSize_whenQueueExceedsCapacity() {
            // arrange — 100명 진입 (배치 크기 N=80 초과)
            for (int i = 1; i <= 100; i++) {
                queueService.enter("user-" + i);
            }
            assertThat(queueService.getTotalCount()).isEqualTo(100L);

            // act
            tokenScheduler.issueTokens();

            // assert — 80명만 처리되고 20명은 대기열에 남음
            assertThat(queueService.getTotalCount()).isEqualTo(20L);
        }

        @DisplayName("스케줄러를 두 번 실행하면 나머지 대기열도 처리된다.")
        @Test
        void processesRemainingQueue_onSecondSchedulerRun() {
            // arrange — 100명 진입
            for (int i = 1; i <= 100; i++) {
                queueService.enter("user-" + i);
            }

            // act — 두 번 실행
            tokenScheduler.issueTokens(); // 80명 처리
            tokenScheduler.issueTokens(); // 나머지 20명 처리

            // assert — 대기열 비어있음
            assertThat(queueService.getTotalCount()).isZero();
        }
    }
}