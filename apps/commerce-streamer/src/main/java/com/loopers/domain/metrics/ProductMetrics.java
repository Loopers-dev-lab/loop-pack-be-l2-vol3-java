package com.loopers.domain.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

@Getter
@Entity
@Table(name = "product_metrics")
public class ProductMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "sale_count", nullable = false)
    private long saleCount;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Version
    private Long version;

    protected ProductMetrics() {}

    public ProductMetrics(Long productId) {
        this.productId = productId;
        this.likeCount = 0;
        this.saleCount = 0;
        this.viewCount = 0;
    }

    public void incrementLikeCount() {
        this.likeCount++;
    }

    public void decrementLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }

    public void incrementViewCount() {
        this.viewCount++;
    }

    public void incrementSaleCount(int quantity) {
        this.saleCount += quantity;
    }

    public void decrementSaleCount(int quantity) {
        this.saleCount = Math.max(0, this.saleCount - quantity);
    }
}
