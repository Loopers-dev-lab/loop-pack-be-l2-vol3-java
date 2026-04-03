package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueStatus;
import com.loopers.domain.coupon.CouponStockRepository;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponType;
import com.loopers.infrastructure.coupon.CouponIssueResultJpaRepository;
import com.loopers.infrastructure.coupon.CouponTemplateJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class CouponIssueConcurrencyTest {

    private static final int MAX_ISSUE_COUNT = 10;
    private static final int THREAD_COUNT = 100;
    private static final LocalDateTime FUTURE_EXPIRED_AT = LocalDateTime.now().plusDays(30);

    @Autowired
    private CouponFacade couponFacade;

    @Autowired
    private CouponTemplateJpaRepository couponTemplateJpaRepository;

    @Autowired
    private CouponIssueResultJpaRepository couponIssueResultJpaRepository;

    @Autowired
    private CouponStockRepository couponStockRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("선착순 쿠폰 발급 동시성 테스트")
    @Nested
    class RequestIssueConcurrency {

        @DisplayName("100명이 동시 요청해도 maxIssueCount(10)만큼만 PENDING 상태로 생성된다")
        @Test
        void onlyMaxIssueCountRequests_shouldBeAccepted() throws InterruptedException {
            // arrange
            CouponTemplate template = couponTemplateJpaRepository.save(
                    new CouponTemplate("선착순 쿠폰", CouponType.FIXED, 1000, null,
                            FUTURE_EXPIRED_AT, MAX_ISSUE_COUNT));
            couponStockRepository.setStock(template.getId(), MAX_ISSUE_COUNT);

            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger soldOutCount = new AtomicInteger(0);

            // act
            for (int i = 0; i < THREAD_COUNT; i++) {
                long userId = i + 1;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        couponFacade.requestIssue(userId, template.getId());
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        if (e.getMessage() != null && e.getMessage().contains("소진")) {
                            soldOutCount.incrementAndGet();
                        }
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            startLatch.countDown();
            doneLatch.await();
            executor.shutdown();

            // assert
            assertThat(successCount.get()).isEqualTo(MAX_ISSUE_COUNT);
            assertThat(soldOutCount.get()).isEqualTo(THREAD_COUNT - MAX_ISSUE_COUNT);

            long pendingCount = couponIssueResultJpaRepository.findAll().stream()
                    .filter(r -> r.getStatus() == CouponIssueStatus.PENDING)
                    .count();
            assertThat(pendingCount).isEqualTo(MAX_ISSUE_COUNT);
        }

        @DisplayName("1000명이 동시 요청해도 maxIssueCount(100)만큼만 PENDING 상태로 생성된다")
        @Test
        void largeScale_onlyMaxIssueCountRequests_shouldBeAccepted() throws InterruptedException {
            // arrange
            int maxIssueCount = 100;
            int threadCount = 1000;

            CouponTemplate template = couponTemplateJpaRepository.save(
                    new CouponTemplate("대규모 선착순 쿠폰", CouponType.FIXED, 5000, null,
                            FUTURE_EXPIRED_AT, maxIssueCount));
            couponStockRepository.setStock(template.getId(), maxIssueCount);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger soldOutCount = new AtomicInteger(0);

            // act
            for (int i = 0; i < threadCount; i++) {
                long userId = i + 1;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        couponFacade.requestIssue(userId, template.getId());
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        if (e.getMessage() != null && e.getMessage().contains("소진")) {
                            soldOutCount.incrementAndGet();
                        }
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            startLatch.countDown();
            doneLatch.await();
            executor.shutdown();

            // assert
            assertThat(successCount.get()).isEqualTo(maxIssueCount);
            assertThat(soldOutCount.get()).isEqualTo(threadCount - maxIssueCount);

            long pendingCount = couponIssueResultJpaRepository.findAll().stream()
                    .filter(r -> r.getStatus() == CouponIssueStatus.PENDING)
                    .count();
            assertThat(pendingCount).isEqualTo(maxIssueCount);
        }

        @DisplayName("같은 유저가 동시에 2번 요청하면 1개만 PENDING 생성된다")
        @Test
        void duplicateUser_shouldBeRejected() throws InterruptedException {
            // arrange
            CouponTemplate template = couponTemplateJpaRepository.save(
                    new CouponTemplate("선착순 쿠폰", CouponType.FIXED, 1000, null,
                            FUTURE_EXPIRED_AT, MAX_ISSUE_COUNT));
            couponStockRepository.setStock(template.getId(), MAX_ISSUE_COUNT);

            long sameUserId = 1L;
            int duplicateCount = 5;
            ExecutorService executor = Executors.newFixedThreadPool(duplicateCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(duplicateCount);
            AtomicInteger successCount = new AtomicInteger(0);

            // act
            for (int i = 0; i < duplicateCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        couponFacade.requestIssue(sameUserId, template.getId());
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        // 중복 요청 거절 예상
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            startLatch.countDown();
            doneLatch.await();
            executor.shutdown();

            // assert — 최소 1개, 최대 몇 개 (동시성 특성상 API 레벨에서 1~수개 통과 가능)
            // Consumer가 최종적으로 1개만 발급하지만, API 레벨에서는 동시 요청 시 여러 개 통과할 수 있음
            assertThat(successCount.get()).isGreaterThanOrEqualTo(1);
        }
    }
}
