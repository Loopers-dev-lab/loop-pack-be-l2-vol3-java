package com.loopers.concurrency;

import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 쿠폰 동시성 제어 테스트
 * - 쿠폰 사용: 원자적 UPDATE (WHERE status='ISSUED')
 * - 쿠폰 발급: 비관적 락 (COUNT-INSERT 갭 방지)
 *
 * [동시 시작 패턴]
 * startLatch(CountDownLatch(1))를 사용하여 모든 스레드가 동시에 출발하도록 보장한다.
 */
@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CouponConcurrencyTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponTemplateRepository couponTemplateRepository;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("동일 쿠폰을 10명이 동시에 사용하면, 정확히 1명만 성공한다")
    void 쿠폰_동시_사용_원자적_UPDATE() throws InterruptedException {
        // arrange
        CouponTemplate template = couponTemplateRepository.save(
                CouponTemplate.define("테스트쿠폰", "설명", DiscountType.FIXED, 1000, null,
                        0, 100, 10,
                        ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(30))
        );

        IssuedCoupon issuedCoupon = issuedCouponRepository.save(
                IssuedCoupon.issue(template.getId(), 1L,
                        template.getName(), template.getDiscountType(),
                        template.getDiscountValue(), template.getMaxDiscountAmount()));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // act
        for (int i = 0; i < threadCount; i++) {
            long orderId = 100L + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    couponService.use(issuedCoupon.getId(), 1L, orderId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        startLatch.countDown();

        latch.await();
        executor.shutdown();

        // assert
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(9);
    }

    @Test
    @DisplayName("최대 5장 발급 가능한 쿠폰에 10명이 동시에 발급 요청하면, 정확히 5명만 성공한다")
    void 쿠폰_동시_발급_비관적_락() throws InterruptedException {
        // arrange
        CouponTemplate template = couponTemplateRepository.save(
                CouponTemplate.define("한정쿠폰", "설명", DiscountType.FIXED, 1000, null,
                        0, 5, 1,
                        ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(30))
        );

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // act
        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1L;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    couponService.issue(template.getId(), userId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        startLatch.countDown();

        latch.await();
        executor.shutdown();

        // assert
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(failCount.get()).isEqualTo(5);

        long totalIssued = issuedCouponRepository.countByCouponTemplateId(template.getId());
        assertThat(totalIssued).isEqualTo(5);
    }
}
