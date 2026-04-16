package com.loopers.infrastructure.ranking.mv;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

import static lombok.AccessLevel.PROTECTED;

/**
 * 월간 랭킹 MV 엔티티.
 * UNIQUE(period_key, product_id) 위반 시 저장이 실패한다.
 * period_key: yyyyMMdd, product_id: product.id, rank: int, score: decimal(24,8), version: int, updated_at: timestamp.
 *
 * 낙관적 락: @Version 필드로 동시 사용 시 커밋 단계에서 충돌 감지·롤백.
 * JPA는 UPDATE 시 {@code WHERE id = ? AND version = ?}를 사용하며, 버전 불일치 시 수정 행 0 → OptimisticLockException. (05-transaction-query §3.1)
 */
@Entity
@Table(
        name = "mv_product_rank_monthly",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_mv_product_rank_monthly_period_product",
                columnNames = {"period_key", "product_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor(access = PROTECTED)
public class MvProductRankMonthlyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "period_key", nullable = false, length = 16)
    private String periodKey;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "`rank`", nullable = false)
    private int rankValue;

    @Column(name = "score", nullable = false, precision = 24, scale = 8)
    private BigDecimal score;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
