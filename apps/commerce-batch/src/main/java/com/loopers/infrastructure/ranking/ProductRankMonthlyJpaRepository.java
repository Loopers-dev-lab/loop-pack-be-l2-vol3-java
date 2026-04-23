package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.ProductRankMonthlyModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRankMonthlyJpaRepository extends JpaRepository<ProductRankMonthlyModel, Long> {

    @Modifying
    @Query("DELETE FROM ProductRankMonthlyModel r WHERE r.yearMonth = :yearMonth")
    void deleteAllByYearMonth(@Param("yearMonth") String yearMonth);
}
