package com.loopers.domain.coupon;

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
public class CouponConcurrencyTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private UserCouponService userCouponService;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @DisplayName("동일한 쿠폰으로 두 곳에서 동시에 사용해도, 1번만 사용된다.")
    @Test
    void concurrentCouponUse() throws InterruptedException {
        // given
        CouponModel coupon = couponService.register(new CouponService.RegisterCommand(
                "3000원 할인", CouponType.FIXED, BigDecimal.valueOf(3000),
                null, ZonedDateTime.now().plusDays(30), 0));
        UserCouponModel userCoupon = userCouponService.issue(coupon.getId(), 1L);
        Long userCouponId = userCoupon.getId();

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // 낙관적 락(@Version) 덕분에 한쪽만 성공, 나머지는 ObjectOptimisticLockingFailureException
                    userCouponService.use(userCouponId, 1L);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();   // 버전 충돌로 실패
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        // then -> 1명 성공, 1명 실패
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);

        // 쿠폰 상태가 USED로 바뀌었는지 확인
        UserCouponModel result = userCouponRepository.findById(userCouponId).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(UserCouponStatus.USED);
    }
}
