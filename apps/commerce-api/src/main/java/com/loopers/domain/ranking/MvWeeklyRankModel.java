package com.loopers.domain.ranking;

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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 주간 랭킹 MV 조회 엔티티 (Batch가 쓰고 API가 읽음).
 *
 * <p>{@link Immutable}로 변경 감지 비활성화 — API는 조회 전용.</p>
 */
@Entity
@Table(name = "mv_product_rank_weekly")
@Immutable
@IdClass(MvWeeklyRankId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MvWeeklyRankModel {

    @Id
    @Column(name = "base_date", nullable = false)
    private LocalDate baseDate;

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
