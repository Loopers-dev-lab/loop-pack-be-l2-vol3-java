package com.loopers.batch.infrastructure.repository;

import com.loopers.batch.infrastructure.entity.ProductRankWeeklyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRankWeeklyJpaRepository extends JpaRepository<ProductRankWeeklyEntity, Long> {

    @Modifying
    @Query("DELETE FROM ProductRankWeeklyEntity e WHERE e.yearWeek = :yearWeek")
    void deleteByYearWeek(@Param("yearWeek") String yearWeek);
}
