package com.loopers.domain.ranking;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "mv_product_rank_weekly", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"product_id", "score_date"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class ProductRankingWeekly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private LocalDate scoreDate;

    @Column(nullable = false)
    private Double score;

    public static ProductRankingWeekly create(Long productId, LocalDate scoreDate, Double score) {
        ProductRankingWeekly ranking = new ProductRankingWeekly();
        ranking.productId = productId;
        ranking.scoreDate = scoreDate;
        ranking.score = score;
        return ranking;
    }
}
