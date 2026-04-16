package com.loopers.infrastructure.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * commerce-streamer Consumer 커스텀 메트릭.
 *
 * <p>catalog-events, order-events, coupon-issue-requests 토픽별
 * 처리/스킵/실패 카운터와 처리 시간 타이머를 등록한다.</p>
 */
@Component
@RequiredArgsConstructor
public class ConsumerMetrics {

    private final MeterRegistry meterRegistry;

    private Counter catalogProcessedCounter;
    private Counter catalogSkippedCounter;
    private Counter catalogFailedCounter;
    private Counter orderProcessedCounter;
    private Counter orderSkippedCounter;
    private Counter couponIssuedCounter;
    private Counter couponRejectedCounter;
    private Timer catalogProcessTimer;
    private Timer orderProcessTimer;
    private Timer couponProcessTimer;

    @PostConstruct
    public void init() {
        // catalog-events
        catalogProcessedCounter = Counter.builder("consumer.catalog.processed")
            .description("catalog-events 정상 처리 건수").register(meterRegistry);
        catalogSkippedCounter = Counter.builder("consumer.catalog.skipped")
            .description("catalog-events 멱등 SKIP 건수").register(meterRegistry);
        catalogFailedCounter = Counter.builder("consumer.catalog.failed")
            .description("catalog-events 처리 실패 건수").register(meterRegistry);
        catalogProcessTimer = Timer.builder("consumer.catalog.duration")
            .description("catalog-events 건별 처리 시간").register(meterRegistry);

        // order-events
        orderProcessedCounter = Counter.builder("consumer.order.processed")
            .description("order-events 정상 처리 건수").register(meterRegistry);
        orderSkippedCounter = Counter.builder("consumer.order.skipped")
            .description("order-events 멱등 SKIP 건수").register(meterRegistry);

        // coupon-issue-requests
        couponIssuedCounter = Counter.builder("consumer.coupon.issued")
            .description("쿠폰 발급 성공 건수").register(meterRegistry);
        couponRejectedCounter = Counter.builder("consumer.coupon.rejected")
            .description("쿠폰 발급 거절 건수").register(meterRegistry);
        couponProcessTimer = Timer.builder("consumer.coupon.duration")
            .description("쿠폰 발급 건별 처리 시간").register(meterRegistry);
    }

    // catalog
    public void recordCatalogProcessed() { catalogProcessedCounter.increment(); }
    public void recordCatalogProcessed(long count) { catalogProcessedCounter.increment(count); }
    public void recordCatalogSkipped() { catalogSkippedCounter.increment(); }
    public void recordCatalogFailed() { catalogFailedCounter.increment(); }
    public void recordCatalogFailed(long count) { catalogFailedCounter.increment(count); }
    public Timer.Sample startCatalogTimer() { return Timer.start(meterRegistry); }
    public void stopCatalogTimer(Timer.Sample sample) { sample.stop(catalogProcessTimer); }

    // order
    public void recordOrderProcessed() { orderProcessedCounter.increment(); }
    public void recordOrderSkipped() { orderSkippedCounter.increment(); }

    // coupon
    public void recordCouponIssued() { couponIssuedCounter.increment(); }
    public void recordCouponRejected() { couponRejectedCounter.increment(); }
    public Timer.Sample startCouponTimer() { return Timer.start(meterRegistry); }
    public void stopCouponTimer(Timer.Sample sample) { sample.stop(couponProcessTimer); }
}
