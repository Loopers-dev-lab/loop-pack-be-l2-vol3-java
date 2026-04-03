package com.loopers.domain.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * 상품 메트릭 집계 테이블.
 *
 * Kafka Consumer가 이벤트를 소비하여 좋아요 수, 주문 수, 조회 수를 집계한다.
 * product_id에 UNIQUE 제약 — 상품당 1개 행.
 * 모든 카운터 증감은 INSERT ... ON DUPLICATE KEY UPDATE 원자적 쿼리로 처리한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "product_metrics")
public class ProductMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "order_count", nullable = false)
    private int orderCount;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;
}
