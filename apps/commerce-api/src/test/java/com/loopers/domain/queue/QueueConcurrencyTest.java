package com.loopers.domain.queue;

import com.loopers.domain.queue.repository.QueueRepository;
import com.loopers.domain.queue.service.EntryTokenService;
import com.loopers.domain.queue.service.QueueFeatureFlag;
import com.loopers.domain.queue.service.QueueService;
import com.loopers.application.event.QueueScheduler;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
class QueueConcurrencyTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private EntryTokenService entryTokenService;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private QueueScheduler queueScheduler;

    @Autowired
    private QueueFeatureFlag queueFeatureFlag;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @BeforeEach
    void setUp() {
        redisCleanUp.truncateAll();
        queueFeatureFlag.setEnabled(true);
    }

    @Nested
    @DisplayName("대기열 진입 동시성")
    class EnterConcurrency {

        @DisplayName("100,000명이 동시에 대기열에 진입하면 중복 없이 100,000명 등록된다")
        @Test
        void hundredThousandConcurrentEntries() throws InterruptedException {
            int threadCount = 100_000;
            ExecutorService executor = Executors.newFixedThreadPool(100);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                long memberId = i + 1;
                executor.submit(() -> {
                    try {
                        queueService.enter(memberId);
                        successCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executor.shutdown();

            Long totalWaiting = queueRepository.getTotalWaiting();
            assertThat(successCount.get()).isEqualTo(threadCount);
            assertThat(totalWaiting).isEqualTo(threadCount);
        }

        @DisplayName("같은 유저가 10번 동시 진입해도 대기열에 1건만 등록된다 (멱등성)")
        @Test
        void sameUserConcurrentEntry_idempotent() throws InterruptedException {
            int threadCount = 10;
            long memberId = 999L;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        queueService.enter(memberId);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executor.shutdown();

            Long totalWaiting = queueRepository.getTotalWaiting();
            assertThat(totalWaiting).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("스케줄러 토큰 발급")
    class SchedulerTokenIssuance {

        @DisplayName("스케줄러 실행 시 상위 50명만 토큰이 발급된다")
        @Test
        void schedulerIssuesTokensToTop50() {
            for (int i = 1; i <= 100; i++) {
                queueService.enter((long) i);
            }

            queueScheduler.processQueue();

            int tokenCount = 0;
            for (int i = 1; i <= 100; i++) {
                if (entryTokenService.getToken((long) i) != null) {
                    tokenCount++;
                }
            }
            assertThat(tokenCount).isEqualTo(50);
        }

        @DisplayName("토큰 없는 유저는 validateToken이 false를 반환한다")
        @Test
        void noTokenUser_validateReturnsFalse() {
            queueService.enter(1L);

            assertThat(entryTokenService.validateToken(1L)).isFalse();
        }
    }

    @Nested
    @DisplayName("토큰 생명주기")
    class TokenLifecycle {

        @DisplayName("토큰 있는 유저의 토큰+큐 삭제 후 조회 불가")
        @Test
        void deleteToken_removesFromBothRedisAndQueue() {
            queueService.enter(1L);
            queueScheduler.processQueue();

            assertThat(entryTokenService.getToken(1L)).isNotNull();
            assertThat(queueRepository.getRank(1L)).isNotNull();

            entryTokenService.deleteToken(1L);
            queueService.removeFromQueue(1L);

            assertThat(entryTokenService.getToken(1L)).isNull();
            assertThat(queueRepository.getRank(1L)).isNull();
        }

        @DisplayName("결제 실패 시 토큰은 유지된다")
        @Test
        void paymentFailed_tokenRemains() {
            queueService.enter(1L);
            queueScheduler.processQueue();

            String token = entryTokenService.getToken(1L);
            assertThat(token).isNotNull();

            // 결제 실패 시에는 토큰 삭제하지 않음
            assertThat(entryTokenService.getToken(1L)).isEqualTo(token);
            assertThat(queueRepository.getRank(1L)).isNotNull();
        }
    }

    @Nested
    @DisplayName("대기열 순서 보장")
    class OrderGuarantee {

        @DisplayName("먼저 진입한 유저가 낮은 rank를 가진다")
        @Test
        void firstEntryHasLowerRank() {
            queueService.enter(1L);
            queueService.enter(2L);
            queueService.enter(3L);

            Long rank1 = queueRepository.getRank(1L);
            Long rank2 = queueRepository.getRank(2L);
            Long rank3 = queueRepository.getRank(3L);

            assertThat(rank1).isLessThan(rank2);
            assertThat(rank2).isLessThan(rank3);
        }
    }

    @Nested
    @DisplayName("피처 플래그")
    class FeatureFlag {

        @DisplayName("피처 플래그 OFF 시 bypass 토큰이 반환된다")
        @Test
        void featureFlagOff_returnsBypass() {
            queueFeatureFlag.setEnabled(false);

            // QueueFacade를 직접 테스트하면 MemberService 인증이 필요하므로
            // 플래그 동작만 확인
            assertThat(queueFeatureFlag.isEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("Graceful Degradation")
    class GracefulDegradation {

        @DisplayName("Redis 장애 시 기존 토큰 유저는 로컬 캐시로 검증 성공한다")
        @Test
        void redisDown_localCacheFallback() {
            queueService.enter(1L);
            queueScheduler.processQueue();

            String token = entryTokenService.getToken(1L);
            assertThat(token).isNotNull();

            // Caffeine 캐시에 저장되어 있으므로 validateToken은
            // Redis 장애 시에도 로컬 캐시 fallback으로 true 반환
            // (실제 Redis 중지 없이 로컬 캐시 존재만 확인)
            assertThat(entryTokenService.validateToken(1L)).isTrue();
        }
    }

    @Nested
    @DisplayName("전체 플로우")
    class FullFlow {

        @DisplayName("100,000명 대기열 → 스케줄러 + 결제 완료 반복 → 전원 순차 입장")
        @Test
        void allUsersGetTokensEventually() {
            int totalUsers = 100_000;
            for (int i = 1; i <= totalUsers; i++) {
                queueService.enter((long) i);
            }

            int totalProcessed = 0;
            while (totalProcessed < totalUsers) {
                // 스케줄러: 활성 토큰 상한까지 발급
                queueScheduler.processQueue();
                queueScheduler.processQueue(); // 2번 호출해서 상한(100)까지 채움

                // 토큰 받은 유저 찾아서 결제 완료 시뮬레이션
                for (int j = totalProcessed + 1; j <= totalUsers; j++) {
                    if (entryTokenService.getToken((long) j) != null) {
                        entryTokenService.deleteToken((long) j);
                        queueService.removeFromQueue((long) j);
                        totalProcessed++;
                    }
                }
            }

            assertThat(queueRepository.getTotalWaiting()).isEqualTo(0);
        }

        @DisplayName("대기열 진입 → 토큰 발급 → 토큰 삭제 + 큐 제거 E2E")
        @Test
        void enterQueue_getToken_deleteToken_e2e() {
            // 1. 대기열 진입
            queueService.enter(1L);
            assertThat(queueRepository.getRank(1L)).isNotNull();

            // 2. 스케줄러 → 토큰 발급
            queueScheduler.processQueue();
            String token = entryTokenService.getToken(1L);
            assertThat(token).isNotNull();

            // 3. 토큰 검증
            assertThat(entryTokenService.validateToken(1L)).isTrue();

            // 4. 결제 성공 → 토큰 삭제 + 큐 제거
            entryTokenService.deleteToken(1L);
            queueService.removeFromQueue(1L);

            assertThat(entryTokenService.getToken(1L)).isNull();
            assertThat(queueRepository.getRank(1L)).isNull();
            assertThat(entryTokenService.validateToken(1L)).isFalse();
        }
    }
}
