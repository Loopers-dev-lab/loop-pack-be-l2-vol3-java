package com.loopers.domain.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Entity
@Table(name = "product_rank_snapshot",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_type_date_product",
                        columnNames = {"ranking_type", "rank_date", "product_id"})
        },
        indexes = {
                @Index(name = "idx_type_date_rank",
                        columnList = "ranking_type, rank_date, rank_position")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductRankSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "ranking_type", nullable = false, length = 20)
    private RankingType rankingType;

    @Column(name = "rank_date", nullable = false)
    private LocalDate rankDate;

    @Column(name = "rank_position", nullable = false)
    private Integer rankPosition;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "price", nullable = false)
    private Integer price;

    @Column(name = "brand_name", nullable = false)
    private String brandName;

    @Column(name = "total_view_count", nullable = false)
    private Long totalViewCount;

    @Column(name = "total_like_count", nullable = false)
    private Long totalLikeCount;

    @Column(name = "total_order_line_count", nullable = false)
    private Long totalOrderLineCount;

    @Column(name = "total_order_amount", nullable = false)
    private Long totalOrderAmount;

    @Column(name = "score", nullable = false)
    private Double score;

    @Builder
    public ProductRankSnapshot(RankingType rankingType, LocalDate rankDate, Integer rankPosition,
                               Long productId, String productName, Integer price, String brandName,
                               Long totalViewCount, Long totalLikeCount,
                               Long totalOrderLineCount, Long totalOrderAmount, Double score) {
        this.rankingType = rankingType;
        this.rankDate = rankDate;
        this.rankPosition = rankPosition;
        this.productId = productId;
        this.productName = productName;
        this.price = price;
        this.brandName = brandName;
        this.totalViewCount = totalViewCount;
        this.totalLikeCount = totalLikeCount;
        this.totalOrderLineCount = totalOrderLineCount;
        this.totalOrderAmount = totalOrderAmount;
        this.score = score;
    }
}
