package com.loopers.concurrency;

import com.loopers.application.coupon.CouponUserService;
import com.loopers.domain.common.Money;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("쿠폰 동시성 테스트")
class CouponConcurrencyTest {

    @Autowired
    private CouponUserService couponUserService;

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
    @DisplayName("동시에 쿠폰을 발급받아도 발급 한도가 유지된다")
    void 동시_쿠폰_발급_한도_유지() throws InterruptedException {
        // Arrange: 최대 10개 발급 가능한 쿠폰
        CouponTemplate template = couponTemplateRepository.save(
            CouponTemplate.createFixed("한정 쿠폰", new Money(5000), Money.ZERO, 10, ZonedDateTime.now().plusDays(7))
        );

        int threadCount = 20;  // 20명이 동시에 발급 시도
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // Act
        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1;
            executor.submit(() -> {
                try {
                    couponUserService.issue(userId, template.getId());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // Assert: 10명만 성공
        assertThat(successCount.get()).isEqualTo(10);
        assertThat(failCount.get()).isEqualTo(10);

        // 발급 수 확인
        CouponTemplate updated = couponTemplateRepository.findById(template.getId()).orElseThrow();
        assertThat(updated.getIssuedCount()).isEqualTo(10);
    }

    @Test
    @DisplayName("같은 사용자가 동시에 같은 쿠폰을 발급받아도 중복 발급되지 않는다")
    void 동시_중복_발급_방지() throws InterruptedException {
        // Arrange
        CouponTemplate template = couponTemplateRepository.save(
            CouponTemplate.createFixed("테스트 쿠폰", new Money(3000), Money.ZERO, 100, ZonedDateTime.now().plusDays(7))
        );

        Long userId = 1L;
        int threadCount = 10;  // 같은 사용자가 10번 동시 발급 시도
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // Act
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    couponUserService.issue(userId, template.getId());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // Assert: 1번만 성공
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(9);

        // 발급된 쿠폰 수 확인
        long issuedCount = issuedCouponRepository.countByUserId(userId);
        assertThat(issuedCount).isEqualTo(1);
    }
}
