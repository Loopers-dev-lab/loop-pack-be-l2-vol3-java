package com.loopers.batch.infrastructure.schema;

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
 * {@code mv_product_rank_monthly} schema-only 매핑. (DDL 생성 전용)
 *
 * <p>현재 앱의 모든 환경은 Hibernate {@code ddl-auto} 로 스키마를 관리한다.
 * {@code sql/V12__create_mv_product_rank_monthly.sql} 은 Flyway 이관 시 사용할 참조 스키마.
 *
 * <p><b>year_month_key 네이밍</b>: MySQL 8.0 parser 는 {@code year_month} 를 unquoted 컬럼명으로 거부한다
 * (ERROR 1064, {@code INTERVAL ... YEAR_MONTH} 문법과의 충돌로 추정). 백틱 wrap 은 가능하지만
 * JdbcTemplate SQL 문자열에 백틱을 박는 건 오타 유인이 커 {@code year_month_key} 접미를 선택했다.
 *
 * <p><b>WARNING</b>: 컬럼/인덱스 변경 시 commerce-api 의 동명 Entity + 참조 SQL 함께 수정.
 * {@link WeeklyRankingMvSchema} 의 WARNING 참조.
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
