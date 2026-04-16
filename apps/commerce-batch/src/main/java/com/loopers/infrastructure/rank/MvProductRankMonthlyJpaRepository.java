package com.loopers.infrastructure.rank;

import com.loopers.domain.rank.MvProductRankId;
import com.loopers.domain.rank.MvProductRankMonthlyModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MvProductRankMonthlyJpaRepository extends JpaRepository<MvProductRankMonthlyModel, MvProductRankId> {

    @Modifying
    @Query("DELETE FROM MvProductRankMonthlyModel m WHERE m.id.periodKey = :periodKey")
    void deleteByPeriodKey(@Param("periodKey") String periodKey);

    @Query("SELECT COUNT(m) FROM MvProductRankMonthlyModel m WHERE m.id.periodKey = :periodKey")
    long countByPeriodKey(@Param("periodKey") String periodKey);
}
