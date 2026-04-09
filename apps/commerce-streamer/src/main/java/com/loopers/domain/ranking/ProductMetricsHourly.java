package com.loopers.domain.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * R9 랭킹 파이프라인 전용 시간 버킷 집계 엔티티.
 *
 * <p>스키마 설계 핵심:
 * <ul>
 *   <li>복합키 {@code (productId, bucketHour)} — 하루 상품당 최대 24 row</li>
 *   <li>{@code bucketHour} = 시간 단위로 절삭된 {@link LocalDateTime} (분/초 = 0)</li>
 *   <li>{@code orderAmount} = 주문 금액 합계 ({@code log1p} 정규화 입력)</li>
 * </ul>
 *
 * <p>증감 API 는 배치 리스너 경로에서 Native UPSERT 로만 기록되며,
 * 본 엔티티 인스턴스는 주로 읽기(집계 결과 매핑)에 사용된다.
 *
 * <p>R7 {@code ProductMetrics} (주석 보존) 와는 완전히 독립된 테이블이다.
 */
@Entity
@Table(name = "product_metrics_hourly")
@IdClass(ProductMetricsHourlyId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMetricsHourly {

    @Id
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Id
    @Column(name = "bucket_hour", nullable = false)
    private LocalDateTime bucketHour;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "order_count", nullable = false)
    private long orderCount;

    @Column(name = "order_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal orderAmount;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public ProductMetricsHourly(
            Long productId,
            LocalDateTime bucketHour,
            long viewCount,
            long likeCount,
            long orderCount,
            BigDecimal orderAmount,
            LocalDateTime updatedAt
    ) {
        this.productId = productId;
        this.bucketHour = bucketHour;
        this.viewCount = viewCount;
        this.likeCount = likeCount;
        this.orderCount = orderCount;
        this.orderAmount = orderAmount == null ? BigDecimal.ZERO : orderAmount;
        this.updatedAt = updatedAt;
    }
}
