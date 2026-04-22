package com.loopers.infrastructure.ranking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 주간 랭킹 MV 읽기 전용 Repository.
 *
 * 쓰기는 commerce-batch의 WeeklyRankingAggregationWriter가 담당.
 * 여기선 조회만 제공.
 *
 * 페이지네이션은 rank_no 범위 쿼리로 수행 (PK 범위 스캔이라 optimal).
 */
public interface MvProductRankWeeklyJpaRepository extends JpaRepository<MvProductRankWeeklyEntity, Long> {

    List<MvProductRankWeeklyEntity> findByYearWeekAndRankNoBetweenOrderByRankNoAsc(
            String yearWeek, int fromRank, int toRank);

    long countByYearWeek(String yearWeek);
}
