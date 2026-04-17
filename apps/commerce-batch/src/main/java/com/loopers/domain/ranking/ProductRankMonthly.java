package com.loopers.domain.ranking;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

import static lombok.AccessLevel.PROTECTED;

@Getter
@NoArgsConstructor(access = PROTECTED)
@Entity
@Table(name = "mv_product_rank_monthly")
public class ProductRankMonthly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "ranking", nullable = false)
    private int ranking;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "sale_count", nullable = false)
    private int saleCount;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "version", nullable = false)
    private long version;

    public ProductRankMonthly(Long productId, int ranking, double score,
                              int viewCount, int likeCount, int saleCount,
                              LocalDate startDate, LocalDate endDate, long version) {
        this.productId = productId;
        this.ranking = ranking;
        this.score = score;
        this.viewCount = viewCount;
        this.likeCount = likeCount;
        this.saleCount = saleCount;
        this.startDate = startDate;
        this.endDate = endDate;
        this.version = version;
    }
}
