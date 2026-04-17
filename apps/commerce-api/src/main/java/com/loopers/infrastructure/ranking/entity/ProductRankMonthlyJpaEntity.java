package com.loopers.infrastructure.ranking.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "mv_product_rank_monthly")
public class ProductRankMonthlyJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false, length = 7)
    private String yearMonth;

    @Column(nullable = false)
    private long likeCount;

    @Column(nullable = false)
    private long orderCount;

    @Column(nullable = false)
    private long viewCount;

    @Column(nullable = false)
    private long totalSalesAmount;

    @Column(nullable = false)
    private double score;

    @Column(nullable = false)
    private int ranking;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
