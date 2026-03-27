package com.loopers.domain.coupon;

import com.loopers.domain.coupon.model.CouponIssueRequest;
import com.loopers.domain.coupon.model.FirstComeCoupon;
import com.loopers.domain.coupon.repository.CouponIssueRequestRepository;
import com.loopers.domain.coupon.service.FirstComeCouponService;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FirstComeCouponScenarioTest {

    @Autowired
    private CouponIssueRequestRepository couponIssueRequestRepository;

    @Autowired
    private FirstComeCouponService firstComeCouponService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final int MAX_QUANTITY = 100;
    private static final int THREAD_COUNT = 200;

    @DisplayName("시나리오 1: DB COUNT 기반 수량 제어")
    @Nested
    class Scenario1_DbCount {

        @DisplayName("200명 동시 요청 시 DB COUNT 기반은 초과 발급이 발생한다")
        @Test
        void overIssuance_whenDbCountOnly() throws InterruptedException {
            // arrange
            Long templateId = 9001L;
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // act — DB COUNT 기반 수량 제어 (Race Condition 발생)
            for (int i = 0; i < THREAD_COUNT; i++) {
                long memberId = i + 1;
                executor.submit(() -> {
                    try {
                        // DB COUNT로 수량 확인 (원자적이지 않음)
                        long count = couponIssueRequestRepository.countByTemplateId(templateId);
                        if (count >= MAX_QUANTITY) {
                            failCount.incrementAndGet();
                            return;
                        }
                        if (couponIssueRequestRepository.existsByTemplateIdAndMemberId(templateId, memberId)) {
                            failCount.incrementAndGet();
                            return;
                        }
                        // 수량 남았으면 INSERT
                        couponIssueRequestRepository.save(CouponIssueRequest.create(templateId, memberId));
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

            // assert — 100장 초과 발급이 발생함을 증명
            long totalIssued = couponIssueRequestRepository.countByTemplateId(templateId);
            System.out.println("[시나리오 1] 발급된 수량: " + totalIssued + " (기대: 100, 실제: 초과)");
            assertThat(totalIssued).isGreaterThan(MAX_QUANTITY);
        }
    }

    @DisplayName("시나리오 2: Redis Sorted Set 기반 수량 제어")
    @Nested
    class Scenario2_RedisSortedSet {

        @DisplayName("200명 동시 요청 시 Redis Sorted Set은 정확히 100명만 성공한다")
        @Test
        void exactQuantity_whenRedisSortedSet() throws InterruptedException {
            // arrange
            Long templateId = 9002L;
            String redisKey = "coupon:fcfs:" + templateId;
            redisTemplate.delete(redisKey);

            FirstComeCoupon fcCoupon = FirstComeCoupon.create(
                    templateId, MAX_QUANTITY,
                    LocalDateTime.now().minusMinutes(1),
                    LocalDateTime.now().plusHours(1));

            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // act
            for (int i = 0; i < THREAD_COUNT; i++) {
                long memberId = i + 1;
                executor.submit(() -> {
                    try {
                        firstComeCouponService.addToQueue(fcCoupon, memberId);
                        successCount.incrementAndGet();
                    } catch (CoreException e) {
                        failCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executor.shutdown();

            // assert
            Long zcard = redisTemplate.opsForZSet().zCard(redisKey);
            System.out.println("[시나리오 2] 성공: " + successCount.get() + ", 실패: " + failCount.get() + ", ZCARD: " + zcard);
            assertThat(successCount.get()).isEqualTo(MAX_QUANTITY);
            assertThat(failCount.get()).isEqualTo(THREAD_COUNT - MAX_QUANTITY);
            assertThat(zcard).isEqualTo(MAX_QUANTITY);

            // cleanup
            redisTemplate.delete(redisKey);
        }
    }

    @DisplayName("시나리오 3: Redis 장애 시 DB Fallback")
    @Nested
    class Scenario3_Fallback {

        @DisplayName("같은 회원이 중복 요청하면 거부된다")
        @Test
        void rejectsDuplicate() {
            // arrange
            Long templateId = 9003L;
            String redisKey = "coupon:fcfs:" + templateId;
            redisTemplate.delete(redisKey);

            FirstComeCoupon fcCoupon = FirstComeCoupon.create(
                    templateId, MAX_QUANTITY,
                    LocalDateTime.now().minusMinutes(1),
                    LocalDateTime.now().plusHours(1));

            // act — 첫 번째 요청 성공
            firstComeCouponService.addToQueue(fcCoupon, 1L);

            // assert — 두 번째 요청 거부
            org.junit.jupiter.api.Assertions.assertThrows(CoreException.class, () ->
                    firstComeCouponService.addToQueue(fcCoupon, 1L));

            // cleanup
            redisTemplate.delete(redisKey);
        }
    }
}
