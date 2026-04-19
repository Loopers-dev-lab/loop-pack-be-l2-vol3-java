package com.loopers.infrastructure.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 주간 랭킹 Materialized View JPA 엔티티 (읽기 전용).
 *
 * 이 엔티티는 commerce-batch 의 weeklyRankingJob 이 JDBC 로 직접 적재하며,
 * commerce-api 는 조회(읽기)만 수행한다. 별도 저장 메서드를 제공하지 않는다.
 *
 * 테이블 구조:
 *   PRIMARY KEY (product_id, base_date) — 날짜별 랭킹 이력을 보존한다.
 *   base_date = batch 실행일 - 1일 (어제 기준 직전 7일 슬라이딩 윈도우 집계)
 *
 * rank 컬럼이 MySQL 8 의 예약어이므로 @Column(name = "`rank`") 로 백틱 인용을 적용한다.
 */
@Entity
@Table(name = "mv_product_rank_weekly")
@IdClass(MvProductRankId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MvProductRankWeekly {

    @Id
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Id
    @Column(name = "base_date", nullable = false)
    private LocalDate baseDate;

    // rank 는 MySQL 8 예약어이므로 백틱으로 인용
    @Column(name = "`rank`", nullable = false)
    private int rank;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
