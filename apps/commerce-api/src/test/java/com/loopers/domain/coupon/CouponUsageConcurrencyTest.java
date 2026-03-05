package com.loopers.domain.coupon;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("쿠폰 사용 동시성 테스트")
class CouponUsageConcurrencyTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("10개 스레드가 동시에 같은 쿠폰을 사용하면 1번만 성공한다")
    void useUserCoupon_Concurrency() throws InterruptedException {
        // Given
        Coupon coupon = couponRepository.save(
                Coupon.create("테스트쿠폰", CouponType.FIXED, new BigDecimal("5000"), null, ZonedDateTime.now().plusDays(30))
        );
        UserCoupon userCoupon = userCouponRepository.save(
                UserCoupon.create(1L, coupon.getId())
        );
        Long userCouponId = userCoupon.getId();
        Long userId = 1L;
        BigDecimal orderAmount = new BigDecimal("50000");

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    couponService.useUserCoupon(userCouponId, userId, orderAmount);
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

        // Then
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(threadCount - 1);

        UserCoupon updatedUserCoupon = userCouponRepository.findById(userCouponId).orElseThrow();
        assertThat(updatedUserCoupon.getStatus()).isEqualTo(CouponStatus.USED);
    }
}
