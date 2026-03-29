package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.IssuedCouponJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("CouponIssueAppService 동시성 테스트")
class CouponIssueAppServiceConcurrencyTest {

    @Autowired
    private CouponIssueAppService couponIssueAppService;

    @Autowired
    private CouponJpaRepository couponJpaRepository;

    @Autowired
    private IssuedCouponJpaRepository issuedCouponJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @Test
    @DisplayName("선착순 쿠폰: N명이 동시에 요청해도 totalQuantity를 초과하지 않는다")
    void 동시_발급_요청_수량_초과_없음() throws InterruptedException {
        // given
        int totalQuantity = 10;
        int requestCount = 30;

        Coupon coupon = couponJpaRepository.save(Coupon.create(
                "선착순 테스트 쿠폰",
                DiscountType.FIXED,
                1000L,
                5000L,
                null,
                totalQuantity,
                ZonedDateTime.now().minusDays(1),
                ZonedDateTime.now().plusDays(1)
        ));
        Long couponId = coupon.getId();

        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch latch = new CountDownLatch(requestCount);

        // when: 30명이 동시에 발급 요청
        for (int i = 1; i <= requestCount; i++) {
            final long userId = i;
            executor.submit(() -> {
                try {
                    couponIssueAppService.processCouponIssue(
                            UUID.randomUUID().toString(), couponId, userId);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // then: 실제 발급된 쿠폰 수가 totalQuantity와 정확히 일치해야 함
        long issuedCount = issuedCouponJpaRepository.count();
        assertThat(issuedCount).isEqualTo(totalQuantity);
    }

    @Test
    @DisplayName("같은 유저가 동시에 중복 요청해도 1번만 발급된다")
    void 동일_유저_중복_요청_1회만_발급() throws InterruptedException {
        // given
        int requestCount = 5;
        long userId = 1L;

        Coupon coupon = couponJpaRepository.save(Coupon.create(
                "중복 방지 테스트 쿠폰",
                DiscountType.FIXED,
                1000L,
                5000L,
                null,
                100,
                ZonedDateTime.now().minusDays(1),
                ZonedDateTime.now().plusDays(1)
        ));
        Long couponId = coupon.getId();

        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch latch = new CountDownLatch(requestCount);

        // when: 같은 userId로 5번 동시 요청
        for (int i = 0; i < requestCount; i++) {
            executor.submit(() -> {
                try {
                    couponIssueAppService.processCouponIssue(
                            UUID.randomUUID().toString(), couponId, userId);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // then: issued_coupons에 1건만 저장되어야 함
        long issuedCount = issuedCouponJpaRepository.count();
        assertThat(issuedCount).isEqualTo(1);
    }
}
