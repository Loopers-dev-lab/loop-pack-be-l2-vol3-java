package com.loopers.application.order;

import com.loopers.application.coupon.CouponApp;
import com.loopers.application.queue.QueueApp;
import com.loopers.application.queue.QueueFallbackProducer;
import com.loopers.config.QueueProperties;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderApp orderApp;
    private final CouponApp couponApp;
    private final QueueApp queueApp;
    private final QueueProperties queueProperties;
    private final QueueFallbackProducer queueFallbackProducer;

    private final Semaphore bulkheadSemaphore = new Semaphore(20);
    private final ConcurrentLinkedQueue<Long> localQueue = new ConcurrentLinkedQueue<>();
    private final Semaphore localQueueSemaphore = new Semaphore(20);
    private final Semaphore kafkaSemaphore = new Semaphore(20);
    private final AtomicLong fallbackOrderCount = new AtomicLong(0);

    @Transactional
    public OrderInfo createOrder(Long memberId, List<OrderItemCommand> items, Long userCouponId, String entryToken) {
        boolean usedFallback = false;

        if (queueProperties.enabled()) {
            usedFallback = validateWithFallback(memberId, entryToken);
        }

        try {
            BigDecimal discountAmount = BigDecimal.ZERO;
            Long refUserCouponId = null;

            if (userCouponId != null) {
                BigDecimal originalAmount = orderApp.calculateOriginalAmount(items);
                discountAmount = couponApp.calculateDiscount(userCouponId, memberId, originalAmount);
                refUserCouponId = couponApp.useUserCoupon(userCouponId);
            }

            OrderInfo orderInfo = orderApp.createOrder(memberId, items, discountAmount, refUserCouponId);

            if (queueProperties.enabled()) {
                consumeTokenSafely(memberId);
            }

            return orderInfo;
        } finally {
            if (usedFallback) {
                releaseFallbackPermit();
            }
        }
    }

    private boolean validateWithFallback(Long memberId, String entryToken) {
        try {
            queueApp.validateToken(memberId, entryToken);
            return false;
        } catch (RedisConnectionFailureException e) {
            return handleRedisFallback(memberId);
        } catch (Exception e) {
            if (isRedisException(e)) {
                return handleRedisFallback(memberId);
            }
            throw e;
        }
    }

    private boolean handleRedisFallback(Long memberId) {
        String strategy = queueProperties.fallbackStrategy();

        if ("BULKHEAD_BYPASS".equals(strategy)) {
            if (!bulkheadSemaphore.tryAcquire()) {
                throw new CoreException(ErrorType.QUEUE_FULL, "Redis 장애 중 동시 주문 한도 초과");
            }
            fallbackOrderCount.incrementAndGet();
            log.warn("[QUEUE_FALLBACK] Bulkhead bypass. memberId={}, fallbackCount={}", memberId, fallbackOrderCount.get());
            return true;
        } else if ("LOCAL_QUEUE".equals(strategy)) {
            localQueue.offer(memberId);
            if (!localQueueSemaphore.tryAcquire()) {
                throw new CoreException(ErrorType.QUEUE_FULL, "Redis 장애 중 로컬 큐 한도 초과");
            }
            fallbackOrderCount.incrementAndGet();
            log.warn("[QUEUE_FALLBACK] Local queue bypass. memberId={}, queueSize={}, fallbackCount={}",
                    memberId, localQueue.size(), fallbackOrderCount.get());
            return true;
        } else if ("KAFKA_FALLBACK".equals(strategy)) {
            if (!kafkaSemaphore.tryAcquire()) {
                throw new CoreException(ErrorType.QUEUE_FULL, "Redis 장애 중 Kafka fallback 한도 초과");
            }
            queueFallbackProducer.publishFallbackOrder(memberId);
            fallbackOrderCount.incrementAndGet();
            log.warn("[QUEUE_FALLBACK] Kafka fallback. memberId={}, fallbackCount={}", memberId, fallbackOrderCount.get());
            return true;
        } else {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "Redis 연결 실패");
        }
    }

    private void releaseFallbackPermit() {
        String strategy = queueProperties.fallbackStrategy();
        if ("BULKHEAD_BYPASS".equals(strategy)) {
            bulkheadSemaphore.release();
        } else if ("LOCAL_QUEUE".equals(strategy)) {
            localQueueSemaphore.release();
        } else if ("KAFKA_FALLBACK".equals(strategy)) {
            kafkaSemaphore.release();
        }
    }

    private void consumeTokenSafely(Long memberId) {
        try {
            queueApp.consumeToken(memberId);
        } catch (Exception e) {
            log.warn("[QUEUE_FALLBACK] Token consume failed (Redis down?). memberId={}", memberId);
        }
    }

    private boolean isRedisException(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof RedisConnectionFailureException ||
                    cause.getClass().getName().contains("Redis")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    @Transactional
    public OrderInfo cancelOrder(Long memberId, String orderId) {
        OrderInfo info = orderApp.cancelOrder(memberId, orderId);
        if (info.refUserCouponId() != null) {
            couponApp.restoreUserCoupon(info.refUserCouponId());
        }
        return info;
    }
}
