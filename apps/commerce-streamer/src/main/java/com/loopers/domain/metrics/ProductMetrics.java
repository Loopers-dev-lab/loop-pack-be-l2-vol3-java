package com.loopers.domain.metrics;

import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
@Table(name = "product_metrics")
public class ProductMetrics {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Column(name = "sale_count", nullable = false)
    private int saleCount;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ProductMetrics() {
    }

    public ProductMetrics(Long productId) {
        this.productId = productId;
        this.likeCount = 0;
        this.viewCount = 0;
        this.saleCount = 0;
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
}
