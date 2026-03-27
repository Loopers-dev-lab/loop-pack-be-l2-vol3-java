package com.loopers.domain.coupon;

import com.loopers.domain.coupon.model.FirstComeCoupon;
import com.loopers.domain.coupon.service.FirstComeCouponService;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
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
class FirstComeCouponConcurrencyTest {

    @Autowired
    private FirstComeCouponService firstComeCouponService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @DisplayName("선착순 100장 쿠폰에 200명이 동시 요청하면 100명만 성공한다")
    @Test
    void onlyMaxQuantitySucceeds_whenConcurrentRequests() throws InterruptedException {
        // arrange
        int maxQuantity = 100;
        int threadCount = 200;
        Long templateId = 999L;
        String redisKey = "coupon:fcfs:" + templateId;

        // Redis 초기화
        redisTemplate.delete(redisKey);

        FirstComeCoupon fcCoupon = FirstComeCoupon.create(
                templateId, maxQuantity,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusHours(1)
        );

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // act
        for (int i = 0; i < threadCount; i++) {
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
        assertThat(successCount.get()).isEqualTo(maxQuantity);
        assertThat(failCount.get()).isEqualTo(threadCount - maxQuantity);
        assertThat(zcard).isEqualTo(maxQuantity);

        // cleanup
        redisTemplate.delete(redisKey);
    }

    @DisplayName("같은 회원이 중복 요청하면 두 번째는 실패한다")
    @Test
    void rejectsDuplicate_whenSameMemberRequests() {
        // arrange
        Long templateId = 998L;
        String redisKey = "coupon:fcfs:" + templateId;
        redisTemplate.delete(redisKey);

        FirstComeCoupon fcCoupon = FirstComeCoupon.create(
                templateId, 100,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusHours(1)
        );

        // act
        firstComeCouponService.addToQueue(fcCoupon, 1L);

        // assert
        org.junit.jupiter.api.Assertions.assertThrows(CoreException.class, () ->
                firstComeCouponService.addToQueue(fcCoupon, 1L));

        // cleanup
        redisTemplate.delete(redisKey);
    }
}
