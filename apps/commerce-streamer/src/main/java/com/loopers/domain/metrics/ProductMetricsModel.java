package com.loopers.domain.metrics;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "product_metrics")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMetricsModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long productId;

    @Column(nullable = false)
    private long viewCount;

    @Column(nullable = false)
    private long likeCount;

    @Column(nullable = false)
    private long salesCount;

    @Column(nullable = false)
    private long salesAmount;

    @Version
    private long version;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public ProductMetricsModel(Long productId) {
        this.productId = productId;
        this.viewCount = 0;
        this.likeCount = 0;
        this.salesCount = 0;
        this.salesAmount = 0;
        this.updatedAt = LocalDateTime.now();
    }

    public void incrementViewCount() {
        this.viewCount++;
        this.updatedAt = LocalDateTime.now();
    }

    public void incrementLikeCount() {
        this.likeCount++;
        this.updatedAt = LocalDateTime.now();
    }

    public void decrementLikeCount() {
        if (this.likeCount > 0) this.likeCount--;
        this.updatedAt = LocalDateTime.now();
    }

    public void addSales(int quantity, long amount) {
        this.salesCount += quantity;
        this.salesAmount += amount;
        this.updatedAt = LocalDateTime.now();
    }
}
