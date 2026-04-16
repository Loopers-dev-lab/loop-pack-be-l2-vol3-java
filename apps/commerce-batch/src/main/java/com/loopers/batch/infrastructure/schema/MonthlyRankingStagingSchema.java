package com.loopers.batch.infrastructure.schema;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * {@code mv_product_rank_monthly_staging} schema-only 매핑. (DDL 생성 전용)
 *
 * <p>현재 앱의 모든 환경은 Hibernate {@code ddl-auto} 로 스키마를 관리한다.
 * {@code sql/V13__create_mv_product_rank_monthly_staging.sql} 은 Flyway 이관 시 사용할 참조 스키마.
 *
 * <p><b>WARNING</b>: 컬럼/인덱스 변경 시 참조 SQL 파일과 함께 수정해야 한다.
 * {@link WeeklyRankingMvSchema} 의 WARNING 참조.
 */
@Entity
@Table(name = "mv_product_rank_monthly_staging")
@IdClass(MonthlyRankingStagingSchema.PK.class)
public class MonthlyRankingStagingSchema {

    @Id
    @Column(name = "year_month_key", nullable = false, length = 7)
    private String yearMonth;

    @Id
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MonthlyRankingStagingSchema() {}

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
