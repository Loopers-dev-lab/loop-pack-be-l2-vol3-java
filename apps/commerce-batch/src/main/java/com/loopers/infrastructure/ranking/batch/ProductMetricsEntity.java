package com.loopers.infrastructure.ranking.batch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

import static lombok.AccessLevel.PROTECTED;

/**
 * 배치가 {@code product_metrics}를 읽기 위한 매핑 엔티티(스트리머와 동일 테이블).
 */
@Entity
@Table(name = "product_metrics")
@Getter
@Setter
@NoArgsConstructor(access = PROTECTED)
public class ProductMetricsEntity {

    @Id
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "sold_quantity", nullable = false)
    private long soldQuantity;

    @Column(name = "last_event_occurred_at")
    private Instant lastEventOccurredAt;

    @Column(name = "last_like_event_occurred_at")
    private Instant lastLikeEventOccurredAt;

    @Column(name = "last_view_event_occurred_at")
    private Instant lastViewEventOccurredAt;

    @Column(name = "last_sold_event_occurred_at")
    private Instant lastSoldEventOccurredAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public static ProductMetricsEntity forRankingRead(
            long productId,
            long likeCount,
            long viewCount,
            long soldQuantity,
            Instant updatedAt
    ) {
        ProductMetricsEntity e = new ProductMetricsEntity();
        e.setProductId(productId);
        e.setLikeCount(likeCount);
        e.setViewCount(viewCount);
        e.setSoldQuantity(soldQuantity);
        e.setUpdatedAt(updatedAt);
        return e;
    }
}
