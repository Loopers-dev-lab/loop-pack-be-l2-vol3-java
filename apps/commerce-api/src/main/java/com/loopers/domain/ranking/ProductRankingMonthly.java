package com.loopers.domain.ranking;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 월간 인기 상품 랭킹 엔티티.
 *
 * <p>배치가 집계한 {@code mv_product_rank_monthly} 테이블의 읽기 전용 매핑이다.</p>
 */
@Entity
@Table(name = "mv_product_rank_monthly")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class ProductRankingMonthly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private LocalDate scoreDate;

    @Column(nullable = false)
    private Double score;

    public static ProductRankingMonthly create(Long productId, LocalDate scoreDate, Double score) {
        ProductRankingMonthly ranking = new ProductRankingMonthly();
        ranking.productId = productId;
        ranking.scoreDate = scoreDate;
        ranking.score = score;
        return ranking;
    }
}
