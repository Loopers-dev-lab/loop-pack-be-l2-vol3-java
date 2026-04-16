package com.loopers.domain.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;

import java.time.LocalDate;

/**
 * 일별 상품 집계 지표 (product_metrics).
 * like_count / order_count 는 해당 날짜(date)의 순증감을 기록한다.
 * commerce-api: 읽기 전용 조회 (오늘 날짜 기준 likeCount 표시용)
 * commerce-streamer: 쓰기 (Kafka Consumer가 (product_id, date) 기준 upsert)
 */
@Getter
@Entity
@Table(
    name = "product_metrics",
    uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "date"})
)
public class ProductMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "order_count", nullable = false)
    private int orderCount;

    protected ProductMetrics() {
    }

    public ProductMetrics(Long productId, LocalDate date, int likeCount) {
        this.productId = productId;
        this.date = date;
        this.likeCount = likeCount;
        this.orderCount = 0;
    }
}
