package com.loopers.domain.metrics;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 상품 지표 집계 엔티티.
 *
 * <p>상품별 조회 수, 좋아요 수, 주문 수, 주문 금액을 실시간 집계한다.
 * Kafka Consumer가 이벤트를 수신하여 upsert로 갱신한다.</p>
 */
@Entity
@Table(name = "product_metrics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMetricsModel {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "order_count", nullable = false)
    private long orderCount;

    @Column(name = "order_amount", nullable = false)
    private long orderAmount;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static ProductMetricsModel create(Long productId) {
        ProductMetricsModel model = new ProductMetricsModel();
        model.productId = productId;
        model.viewCount = 0;
        model.likeCount = 0;
        model.orderCount = 0;
        model.orderAmount = 0;
        model.updatedAt = LocalDateTime.now();
        return model;
    }
}
