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
 * {@code mv_product_rank_weekly} schema-only 매핑. (DDL 생성 전용)
 *
 * <p>runtime 코드는 JdbcTemplate 으로 이 테이블을 조회/변경한다.
 *
 * <p><b>현재 DDL 관리 방식</b>: 프로젝트에 Flyway 가 아직 도입되지 않아 **앱의 모든 환경은
 * Hibernate {@code ddl-auto} 로 스키마를 관리**한다. {@code sql/V10__create_mv_product_rank_weekly.sql}
 * 은 앞으로 Flyway 이관 시 사용할 참조 스키마로 미리 만들어둔 파일이며, 현재 런타임에서 실행되지 않는다.
 *
 * <p><b>WARNING — 2중 정의 주의</b>: 이 Entity 와 commerce-api 의 동명 Entity, 그리고 Flyway 이관용
 * {@code sql/V10} 이 같은 테이블을 각자 정의한다. 컬럼/인덱스 변경 시 <b>세 곳을 함께 수정</b>해야
 * 하며, 그렇지 않으면 모듈 간 drift 및 추후 Flyway 이관 시 실제 DB 와 migration 불일치가 발생한다.
 * 장기적으로는 Flyway 도입으로 진실 공급원을 하나로 통합하는 것이 이상적이며, 이는 차기 스프린트
 * 과제로 기록되어 있다.
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
