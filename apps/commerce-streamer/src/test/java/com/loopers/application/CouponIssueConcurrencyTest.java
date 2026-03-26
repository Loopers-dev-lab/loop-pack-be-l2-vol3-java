package com.loopers.application;

import com.loopers.domain.coupon.CouponIssueResultModel;
import com.loopers.domain.coupon.CouponIssueStatus;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.coupon.event.CouponIssueMessage;
import com.loopers.infrastructure.coupon.CouponIssueResultJpaRepository;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CouponIssueConcurrencyTest {

    @Autowired
    private CouponIssueProcessor couponIssueProcessor;

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponJpaRepository couponJpaRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private CouponIssueResultJpaRepository couponIssueResultJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private CouponModel createCoupon(int totalQuantity) {
        CouponModel coupon = BeanUtils.instantiateClass(CouponModel.class);
        ReflectionTestUtils.setField(coupon, "totalQuantity", totalQuantity);
        ReflectionTestUtils.setField(coupon, "issuedQuantity", 0);
        return couponJpaRepository.save(coupon);
    }

    private void createPendingResult(String requestId, Long couponId, Long memberId) {
        CouponIssueResultModel result = BeanUtils.instantiateClass(CouponIssueResultModel.class);
        ReflectionTestUtils.setField(result, "requestId", requestId);
        ReflectionTestUtils.setField(result, "couponId", couponId);
        ReflectionTestUtils.setField(result, "memberId", memberId);
        ReflectionTestUtils.setField(result, "status", CouponIssueStatus.PENDING);
        ReflectionTestUtils.setField(result, "createdAt", LocalDateTime.now());
        couponIssueResultJpaRepository.save(result);
    }

    @DisplayName("선착순 쿠폰 동시 발급 시,")
    @Nested
    class ConcurrentIssue {

        @DisplayName("100장 쿠폰에 300명이 동시 요청해도 100장만 발급된다")
        @Test
        void concurrentIssueLimitsQuantity() throws InterruptedException {
            // given
            int totalQuantity = 100;
            int requestCount = 300;
            CouponModel coupon = createCoupon(totalQuantity);
            Long couponId = coupon.getId();

            ExecutorService executor = Executors.newFixedThreadPool(30);
            CountDownLatch latch = new CountDownLatch(requestCount);
            AtomicInteger successCount = new AtomicInteger();
            AtomicInteger failCount = new AtomicInteger();

            String[] requestIds = new String[requestCount];
            for (int i = 0; i < requestCount; i++) {
                requestIds[i] = UUID.randomUUID().toString();
                createPendingResult(requestIds[i], couponId, (long) (i + 1));
            }

            // when
            for (int i = 0; i < requestCount; i++) {
                long memberId = i + 1;
                String requestId = requestIds[i];
                executor.submit(() -> {
                    try {
                        couponIssueProcessor.process(
                                new CouponIssueMessage(
                                        requestId, couponId, memberId, LocalDateTime.now()));
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

            // then
            CouponModel result = couponService.getByIdWithLock(couponId);
            assertThat(result.getRemainingQuantity()).isEqualTo(0);
            assertThat(successCount.get() + failCount.get()).isEqualTo(requestCount);
        }
    }

    @DisplayName("중복 발급 방지 시,")
    @Nested
    class DuplicateIssue {

        @DisplayName("같은 회원이 같은 쿠폰을 두 번 요청해도 한 번만 발급된다")
        @Test
        void duplicateIssuePrevented() {
            // given
            CouponModel coupon = createCoupon(100);
            Long couponId = coupon.getId();
            Long memberId = 1L;

            String requestId1 = UUID.randomUUID().toString();
            String requestId2 = UUID.randomUUID().toString();
            createPendingResult(requestId1, couponId, memberId);
            createPendingResult(requestId2, couponId, memberId);

            // when
            couponIssueProcessor.process(
                    new CouponIssueMessage(requestId1, couponId, memberId, LocalDateTime.now()));
            couponIssueProcessor.process(
                    new CouponIssueMessage(requestId2, couponId, memberId, LocalDateTime.now()));

            // then
            assertThat(userCouponRepository.existsByCouponIdAndMemberId(couponId, memberId)).isTrue();

            CouponModel result = couponService.getByIdWithLock(couponId);
            assertThat(result.getIssuedQuantity()).isEqualTo(1);
        }
    }
}
