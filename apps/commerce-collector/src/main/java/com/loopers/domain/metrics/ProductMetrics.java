package com.loopers.domain.metrics;

import java.time.Instant;
import java.util.Objects;

/**
 * 상품 메트릭 집계 데이터.
 */
public class ProductMetrics {

    private final Long id;
    private final Long productId;
    private long likeCount;
    private long viewCount;
    private long orderCount;
    private long orderTotalAmount;
    private Instant updatedAt;
    private Instant lastEventTimestamp;  // 마지막으로 처리된 이벤트의 타임스탬프

    private ProductMetrics(
        Long id,
        Long productId,
        long likeCount,
        long viewCount,
        long orderCount,
        long orderTotalAmount,
        Instant updatedAt,
        Instant lastEventTimestamp
    ) {
        this.id = id;
        this.productId = Objects.requireNonNull(productId, "productId must not be null");
        this.likeCount = likeCount;
        this.viewCount = viewCount;
        this.orderCount = orderCount;
        this.orderTotalAmount = orderTotalAmount;
        this.updatedAt = updatedAt;
        this.lastEventTimestamp = lastEventTimestamp;
    }

    public static ProductMetrics create(Long productId) {
        return new ProductMetrics(null, productId, 0, 0, 0, 0, Instant.now(), null);
    }

    public static ProductMetrics reconstitute(
        Long id,
        Long productId,
        long likeCount,
        long viewCount,
        long orderCount,
        long orderTotalAmount,
        Instant updatedAt,
        Instant lastEventTimestamp
    ) {
        return new ProductMetrics(id, productId, likeCount, viewCount, orderCount, orderTotalAmount, updatedAt, lastEventTimestamp);
    }

    /**
     * 이벤트 타임스탬프가 마지막 처리된 이벤트보다 새로운지 확인.
     * @param eventTimestamp 처리할 이벤트의 타임스탬프
     * @return 이벤트가 최신이거나 처음인 경우 true
     */
    public boolean isNewerEvent(Instant eventTimestamp) {
        if (lastEventTimestamp == null) {
            return true;
        }
        return eventTimestamp.isAfter(lastEventTimestamp);
    }

    /**
     * 마지막 이벤트 타임스탬프 업데이트.
     * 현재 저장된 것보다 새로운 경우에만 업데이트.
     */
    public void updateLastEventTimestamp(Instant eventTimestamp) {
        if (lastEventTimestamp == null || eventTimestamp.isAfter(lastEventTimestamp)) {
            this.lastEventTimestamp = eventTimestamp;
        }
    }

    public void incrementLikeCount() {
        this.likeCount++;
        this.updatedAt = Instant.now();
    }

    public void decrementLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
        this.updatedAt = Instant.now();
    }

    public void incrementViewCount() {
        this.viewCount++;
        this.updatedAt = Instant.now();
    }

    public void addOrder(int quantity, long amount) {
        this.orderCount += quantity;
        this.orderTotalAmount += amount;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public long getViewCount() {
        return viewCount;
    }

    public long getOrderCount() {
        return orderCount;
    }

    public long getOrderTotalAmount() {
        return orderTotalAmount;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getLastEventTimestamp() {
        return lastEventTimestamp;
    }
}
