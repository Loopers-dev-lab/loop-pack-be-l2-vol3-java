package com.loopers.batch.infrastructure.schema;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * {@code ranking_score_ledger} 테이블의 schema-only 매핑.
 *
 * <p>이 클래스는 runtime 에서 직접 사용되지 않는다. commerce-batch 가 commerce-streamer 를
 * 의존하지 않는 경계를 유지하면서도, 테스트 환경에서 Hibernate {@code ddl-auto: create} 가
 * 동일한 테이블을 생성할 수 있도록 구조만 선언한다.
 *
 * <p>현재 프로젝트 전체가 Flyway 를 사용하지 않고 {@code ddl-auto} 로 스키마를 관리한다.
 * {@code sql/V8__create_ranking_score_ledger.sql} 은 Flyway 이관 시 참조 스키마로 미리 만들어둔 것.
 *
 * <p><b>WARNING — 2중 정의 주의</b>: 이 Entity 는 commerce-streamer 의
 * {@code com.loopers.domain.ranking.RankingScoreLedger} 와 <b>같은 테이블을 각자 정의</b>하고 있다.
 * Ledger 스키마 변경 시 (1) streamer 의 엔티티, (2) 이 파일, (3) Flyway 이관용 참조 SQL 을
 * <b>모두 함께 수정</b>해야 한다.
 */
@Entity
@Table(
    name = "ranking_score_ledger",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_ranking_score_ledger_bucket_product",
        columnNames = {"bucket_type", "bucket_key", "product_id"}
    ),
    indexes = @Index(
        name = "idx_ranking_score_ledger_dirty",
        columnList = "bucket_type, bucket_key, dirty"
    )
)
public class RankingScoreLedgerSchema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bucket_type", nullable = false, length = 8)
    private String bucketType;

    @Column(name = "bucket_key", nullable = false, length = 16)
    private String bucketKey;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "base_points", nullable = false)
    private double basePoints;

    @Column(name = "last_scored_at", nullable = false)
    private Instant lastScoredAt;

    @Column(name = "dirty", nullable = false)
    private boolean dirty;

    @Column(name = "version")
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected RankingScoreLedgerSchema() {}
}
