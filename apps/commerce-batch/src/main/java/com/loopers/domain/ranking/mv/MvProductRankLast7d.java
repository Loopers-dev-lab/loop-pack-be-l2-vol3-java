package com.loopers.domain.ranking.mv;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 롤링 7일 랭킹 확정 MV — TOP 100 만 저장되는 조회 전용 영속화 테이블.
 *
 * <p>배치 도중에는 MV 에 "비어있음" 또는 "확정된 TOP 100" 두 상태만 존재하도록 설계됐다
 * (중간 상태 불가시성, 설계.md 프롤로그 "확정됨(committed)" 원칙).</p>
 *
 * <p>Step 4a 가 해당 anchor_date 의 row 를 사전 DELETE 하고,
 * Step 5b 가 Top 100 을 단일 SQL INSERT 로 채운다.</p>
 */
@Entity
@Table(
        name = "mv_product_rank_last_7d",
        indexes = @Index(
                name = "idx_last_7d_rank",
                columnList = "anchor_date, weight_group, rank_position"
        )
)
@IdClass(MvProductRankId.class)
@Getter
public class MvProductRankLast7d {

    @Id
    @Column(name = "anchor_date", nullable = false)
    private LocalDate anchorDate;

    @Id
    @Column(name = "weight_group", length = 32, nullable = false)
    private String weightGroup;

    @Id
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "sales_amount", nullable = false)
    private long salesAmount;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "rank_position", nullable = false)
    private int rankPosition;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected MvProductRankLast7d() {
    }

    public MvProductRankLast7d(LocalDate anchorDate, String weightGroup, Long productId,
                               long viewCount, long likeCount, long salesAmount,
                               double score, int rankPosition, LocalDateTime createdAt) {
        this.anchorDate = anchorDate;
        this.weightGroup = weightGroup;
        this.productId = productId;
        this.viewCount = viewCount;
        this.likeCount = likeCount;
        this.salesAmount = salesAmount;
        this.score = score;
        this.rankPosition = rankPosition;
        this.createdAt = createdAt;
    }
}
