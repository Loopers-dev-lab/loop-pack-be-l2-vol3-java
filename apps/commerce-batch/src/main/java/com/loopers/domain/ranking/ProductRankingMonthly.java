package com.loopers.domain.ranking;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "mv_product_rank_monthly", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"product_id", "score_date"})
}, indexes = {
        @Index(name = "idx_score_date_score", columnList = "score_date, score DESC")
})
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
