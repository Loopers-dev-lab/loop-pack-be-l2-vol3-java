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
 * {@code mv_product_rank_weekly} schema-only 매핑.
 *
 * <p>runtime 쿼리는 {@code WeeklyRankingRepositoryImpl} 이 JdbcTemplate 으로 수행한다.
 * 이 클래스는 Hibernate {@code ddl-auto} 가 동일 테이블을 생성할 수 있게 구조만 선언한다.
 *
 * <p>현재 프로젝트는 Flyway 를 쓰지 않고 {@code ddl-auto} 로 DDL 을 관리한다.
 * {@code sql/V10__create_mv_product_rank_weekly.sql} 은 Flyway 이관 시 참조 스키마로 준비된 파일.
 *
 * <p><b>WARNING — 3중 정의 주의</b>: 이 Entity 와 (1) commerce-batch 의 동명 Entity, (2) 참조 SQL 이
 * 같은 테이블을 각자 정의한다. 컬럼/인덱스 변경 시 세 곳을 함께 수정해야 drift 를 막을 수 있다.
 */
@Entity
@Table(
    name = "mv_product_rank_weekly",
    indexes = @Index(
        name = "idx_mv_product_rank_weekly_position",
        columnList = "year_week, ranking_position"
    )
)
@IdClass(WeeklyRankingMvSchema.PK.class)
public class WeeklyRankingMvSchema {

    @Id
    @Column(name = "year_week", nullable = false, length = 10)
    private String yearWeek;

    @Id
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "ranking_position", nullable = false)
    private long rankingPosition;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WeeklyRankingMvSchema() {}

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
