package com.loopers.domain.coupon;

import com.loopers.support.enums.DiscountType;
import com.loopers.support.error.CoreException;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("쿠폰 발급 동시성 테스트")
class CouponIssuanceConcurrencyTest {

    @Autowired
    CouponService couponService;

    @Autowired
    UserCouponRepository userCouponRepository;

    @Autowired
    DatabaseCleanUp databaseCleanUp;

    private Long couponId;

    @BeforeEach
    void setUp() {
        CouponModel coupon = couponService.createCoupon(
                "동시성테스트쿠폰", DiscountType.FIXED, BigDecimal.valueOf(1000),
                null, LocalDateTime.now().plusDays(30), null);
        couponId = coupon.getCouponId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("같은 사용자가 동시에 같은 쿠폰을 2회 발급 요청 시 1건만 성공한다")
    void concurrentIssueSameUser_ShouldIssueOnlyOnce() throws InterruptedException {
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    couponService.issueCoupon(1L, couponId);
                    successCount.incrementAndGet();
                } catch (CoreException e) {
                    failCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);

        List<UserCouponModel> issued = userCouponRepository.findAllByUserId(1L);
        assertThat(issued).hasSize(1);
    }

    @Test
    @DisplayName("50명이 동시에 동일 쿠폰 발급 시 중복 발급 없이 정확히 처리된다")
    void concurrentIssueDifferentUsers_ShouldNotDuplicate() throws InterruptedException {
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        List<Long> userIds = new ArrayList<>();
        for (long i = 1; i <= threadCount; i++) {
            userIds.add(i);
        }

        for (int i = 0; i < threadCount; i++) {
            final Long userId = userIds.get(i);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    couponService.issueCoupon(userId, couponId);
                    successCount.incrementAndGet();
                } catch (CoreException | InterruptedException e) {
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        // 50명 각각 다른 userId → 모두 성공해야 함
        assertThat(successCount.get()).isEqualTo(threadCount);
    }
}
