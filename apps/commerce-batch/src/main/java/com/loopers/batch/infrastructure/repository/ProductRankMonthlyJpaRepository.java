package com.loopers.batch.infrastructure.repository;

import com.loopers.batch.infrastructure.entity.ProductRankMonthlyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRankMonthlyJpaRepository extends JpaRepository<ProductRankMonthlyEntity, Long> {

    @Modifying
    @Query("DELETE FROM ProductRankMonthlyEntity e WHERE e.yearMonth = :yearMonth")
    void deleteByYearMonth(@Param("yearMonth") String yearMonth);
}
