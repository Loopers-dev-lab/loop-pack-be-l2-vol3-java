package com.loopers.infrastructure.ranking.mv;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MvProductRankMonthlyJpaRepository extends JpaRepository<MvProductRankMonthlyEntity, Long> {

    /**
     * 주간 랭킹 MV 최대 버전을 조회한다.
     *
     * @param periodKey 기간 키
     * @return 주간 랭킹 MV 최대 버전
     */
    @Query("select max(e.version) from MvProductRankMonthlyEntity e where e.periodKey = ?1")
    Integer findMaxVersionByPeriodKey(String periodKey);

    List<MvProductRankMonthlyEntity> findByPeriodKeyAndVersionOrderByRankValueAsc(String periodKey, int version);
}
