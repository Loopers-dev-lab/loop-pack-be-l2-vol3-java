package com.loopers.batch.infrastructure.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "product_metrics")
public class ProductMetricsEntity {

    @Id
    private Long productId;

    @Column(nullable = false)
    private long likeCount;

    @Column(nullable = false)
    private long orderCount;

    @Column(nullable = false)
    private long viewCount;

    @Column(nullable = false)
    private long totalSalesAmount;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
