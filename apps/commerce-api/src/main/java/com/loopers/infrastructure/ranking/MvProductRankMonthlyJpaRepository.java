package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRankId;
import com.loopers.domain.ranking.MvProductRankMonthlyModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MvProductRankMonthlyJpaRepository extends JpaRepository<MvProductRankMonthlyModel, MvProductRankId> {

    @Query("SELECT COUNT(m) FROM MvProductRankMonthlyModel m WHERE m.id.periodKey = :periodKey")
    long countByPeriodKey(@Param("periodKey") String periodKey);
}
