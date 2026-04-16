package com.loopers.infrastructure.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 월간 랭킹 MV 조회 엔티티.
 */
@Entity
@Table(name = "mv_product_rank_monthly")
@Immutable
@IdClass(MvMonthlyRankId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MvMonthlyRankModel {

    @Id
    @Column(name = "`year_month`", length = 7, nullable = false)
    private String yearMonth;

    @Id
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "rank_no", nullable = false)
    private int rankNo;

    @Column(name = "score", nullable = false)
    private BigDecimal score;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "order_count", nullable = false)
    private long orderCount;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "order_amount", nullable = false)
    private long orderAmount;

    @Column(name = "aggregated_at", nullable = false)
    private LocalDateTime aggregatedAt;
}
