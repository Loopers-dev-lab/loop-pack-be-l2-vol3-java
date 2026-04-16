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
 * commerce-streamer: Kafka 이벤트 소비 후 (product_id, date) 기준으로 upsert한다.
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

    public ProductMetrics(Long productId, LocalDate date, int likeCount, int orderCount) {
        this.productId = productId;
        this.date = date;
        this.likeCount = likeCount;
        this.orderCount = orderCount;
    }

    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }

    public void increaseOrderCount() {
        this.orderCount++;
    }
}
