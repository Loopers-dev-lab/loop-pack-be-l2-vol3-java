package com.loopers.infrastructure.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Immutable
@Entity
@Table(name = "mv_product_rank_weekly")
@IdClass(MvProductRankWeeklyEntity.PK.class)
public class MvProductRankWeeklyEntity {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Id
    @Column(name = "week_start_date")
    private LocalDate weekStartDate;

    @Column(name = "total_score")
    private double totalScore;

    @Column(name = "rank_position")
    private int rankPosition;

    @Column(name = "aggregated_at")
    private LocalDateTime aggregatedAt;

    public Long getProductId() { return productId; }
    public LocalDate getWeekStartDate() { return weekStartDate; }
    public double getTotalScore() { return totalScore; }
    public int getRankPosition() { return rankPosition; }

    public static class PK implements Serializable {
        private Long productId;
        private LocalDate weekStartDate;

        protected PK() {}

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK pk)) return false;
            return Objects.equals(productId, pk.productId) && Objects.equals(weekStartDate, pk.weekStartDate);
        }

        @Override
        public int hashCode() { return Objects.hash(productId, weekStartDate); }
    }
}
