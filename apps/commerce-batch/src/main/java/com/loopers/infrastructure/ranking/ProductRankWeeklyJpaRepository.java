package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.ProductRankWeeklyModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRankWeeklyJpaRepository extends JpaRepository<ProductRankWeeklyModel, Long> {

    @Modifying
    @Query("DELETE FROM ProductRankWeeklyModel r WHERE r.yearWeek = :yearWeek")
    void deleteAllByYearWeek(@Param("yearWeek") String yearWeek);
}
