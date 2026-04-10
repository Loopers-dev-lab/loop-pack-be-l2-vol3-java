package com.loopers.domain.ranking;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "product_ranking_scores",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ranking_product_date",
                        columnNames = {"product_id", "ranking_date"})},
        indexes = {
                @Index(name = "idx_ranking_date_score",
                        columnList = "ranking_date, score")})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductRankingScoreModel extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "ranking_date", nullable = false)
    private LocalDate rankingDate;

    @Column(name = "score", nullable = false)
    private double score;

    // === 생성 === //

    private ProductRankingScoreModel(Long productId, LocalDate rankingDate, double score) {
        this.productId = productId;
        this.rankingDate = rankingDate;
        this.score = score;
    }

    public static ProductRankingScoreModel create(Long productId, LocalDate rankingDate, double initialScore) {
        return new ProductRankingScoreModel(productId, rankingDate, initialScore);
    }

    // === 도메인 로직 === //

    public void addScore(double delta) {
        this.score += delta;
    }

    public ProductRankingScoreModel createCarriedOver(LocalDate targetDate, double carryOverRate) {
        return new ProductRankingScoreModel(
                this.productId,
                targetDate,
                this.score * carryOverRate
        );
    }
}
