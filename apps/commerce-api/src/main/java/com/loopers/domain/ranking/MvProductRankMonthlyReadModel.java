package com.loopers.domain.ranking;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "mv_product_rank_monthly")
@Getter
public class MvProductRankMonthlyReadModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private long viewCount;

    @Column(nullable = false)
    private long likeCount;

    @Column(nullable = false)
    private long salesCount;

    @Column(nullable = false)
    private double score;

    @Column(name = "`ranking`", nullable = false)
    private int ranking;

    @Column(name = "`year_month`", nullable = false, length = 10)
    private String yearMonth;

    protected MvProductRankMonthlyReadModel() {}
}
