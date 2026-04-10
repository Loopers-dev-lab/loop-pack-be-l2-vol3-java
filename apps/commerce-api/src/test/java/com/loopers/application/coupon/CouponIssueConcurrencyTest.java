package com.loopers.application.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.domain.coupon.CouponDiscountType;
import com.loopers.domain.coupon.CouponIssueLimiter;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.OwnedCouponJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import java.time.ZonedDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@DisplayName("선착순 쿠폰 발급 동시성 통합 테스트 (Redis + Kafka)")
@SpringBootTest
class CouponIssueConcurrencyTest {

    @Autowired
    private CouponFacade couponFacade;

    @Autowired
    private CouponJpaRepository couponJpaRepository;

    @Autowired
    private OwnedCouponJpaRepository ownedCouponJpaRepository;

    @Autowired
    private CouponIssueLimiter couponIssueLimiter;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @BeforeEach
    void setUp() {
        redisCleanUp.truncateAll();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("100장 쿠폰에 1000명이 동시 요청하면 정확히 100장만 발급된다")
    @Test
    void issueCoupon_concurrency_exactQuantity() throws InterruptedException {
        // arrange
        int totalQuantity = 100;
        int threadCount = 1000;

        CouponModel coupon = couponJpaRepository.save(CouponModel.create(
                "선착순 쿠폰", CouponDiscountType.FIXED, 5000L,
                null, totalQuantity, ZonedDateTime.now().plusDays(30)));
        couponIssueLimiter.registerTotalQuantity(coupon.getId(), totalQuantity);

        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // act
        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1;
            executorService.submit(() -> {
                try {
                    couponFacade.issueCoupon(coupon.getId(), userId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executorService.shutdown();

        // assert — Kafka Consumer가 비동기로 처리하므로 대기
        long deadline = System.currentTimeMillis() + 30_000;
        while (ownedCouponJpaRepository.countByCouponId(coupon.getId()) < totalQuantity
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(500);
        }

        long ownedCount = ownedCouponJpaRepository.countByCouponId(coupon.getId());
        assertAll(
                () -> assertThat(successCount.get()).isEqualTo(totalQuantity),
                () -> assertThat(failCount.get()).isEqualTo(threadCount - totalQuantity),
                () -> assertThat(ownedCount).isEqualTo(totalQuantity));
    }
}
