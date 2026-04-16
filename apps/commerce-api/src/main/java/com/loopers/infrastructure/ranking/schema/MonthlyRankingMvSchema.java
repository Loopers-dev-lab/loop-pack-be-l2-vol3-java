package com.loopers.infrastructure.ranking.schema;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * {@code mv_product_rank_monthly} schema-only 매핑.
 *
 * <p>runtime 쿼리는 {@code MonthlyRankingRepositoryImpl} 이 JdbcTemplate 으로 수행한다.
 * 현재 프로젝트는 {@code ddl-auto} 로 DDL 을 관리한다. {@code sql/V12} 는 Flyway 이관 시 참조 스키마.
 *
 * <p><b>WARNING</b>: commerce-batch 쪽 Entity, 이 Entity, 참조 SQL 세 곳에서 같은 테이블을 각자 정의한다.
 * {@link WeeklyRankingMvSchema} 의 WARNING 참조. {@code year_month_key} 네이밍 근거는
 * commerce-batch 쪽 {@code MonthlyRankingMvSchema} 주석 참조.
 */
@Entity
@Table(
    name = "mv_product_rank_monthly",
    indexes = @Index(
        name = "idx_mv_product_rank_monthly_position",
        columnList = "year_month_key, ranking_position"
    )
)
@IdClass(MonthlyRankingMvSchema.PK.class)
public class MonthlyRankingMvSchema {

    @Id
    @Column(name = "year_month_key", nullable = false, length = 7)
    private String yearMonth;

    @Id
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "ranking_position", nullable = false)
    private long rankingPosition;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MonthlyRankingMvSchema() {}

    public static class PK implements Serializable {
        private String yearMonth;
        private Long productId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK pk)) return false;
            return Objects.equals(yearMonth, pk.yearMonth) && Objects.equals(productId, pk.productId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(yearMonth, productId);
        }
    }
}
