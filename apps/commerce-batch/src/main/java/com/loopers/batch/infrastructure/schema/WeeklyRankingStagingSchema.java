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
 * {@code mv_product_rank_weekly_staging} schema-only 매핑. (DDL 생성 전용)
 *
 * <p>현재 앱의 모든 환경은 Hibernate {@code ddl-auto} 로 스키마를 관리한다.
 * {@code sql/V11__create_mv_product_rank_weekly_staging.sql} 은 Flyway 이관 시 사용할 참조 스키마.
 *
 * <p><b>WARNING</b>: 컬럼/인덱스 변경 시 참조 SQL 파일과 함께 수정해야 한다.
 * {@link WeeklyRankingMvSchema} 의 WARNING 참조.
 */
@Entity
@Table(name = "mv_product_rank_weekly_staging")
@IdClass(WeeklyRankingStagingSchema.PK.class)
public class WeeklyRankingStagingSchema {

    @Id
    @Column(name = "year_week", nullable = false, length = 10)
    private String yearWeek;

    @Id
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WeeklyRankingStagingSchema() {}

    public static class PK implements Serializable {
        private String yearWeek;
        private Long productId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK pk)) return false;
            return Objects.equals(yearWeek, pk.yearWeek) && Objects.equals(productId, pk.productId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(yearWeek, productId);
        }
    }
}
