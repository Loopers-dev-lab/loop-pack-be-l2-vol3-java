package com.loopers.infrastructure.ranking;

import com.loopers.infrastructure.ranking.entity.ProductRankMonthlyJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRankMonthlyJpaRepository extends JpaRepository<ProductRankMonthlyJpaEntity, Long> {

    @Query("SELECT e FROM ProductRankMonthlyJpaEntity e WHERE e.yearMonth = :yearMonth ORDER BY e.ranking ASC")
    List<ProductRankMonthlyJpaEntity> findByYearMonthOrderByRanking(
            @Param("yearMonth") String yearMonth, Pageable pageable);

    long countByYearMonth(String yearMonth);
}
