package com.loopers.infrastructure.ranking;

import com.loopers.infrastructure.ranking.entity.ProductRankWeeklyJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRankWeeklyJpaRepository extends JpaRepository<ProductRankWeeklyJpaEntity, Long> {

    @Query("SELECT e FROM ProductRankWeeklyJpaEntity e WHERE e.yearWeek = :yearWeek ORDER BY e.ranking ASC")
    List<ProductRankWeeklyJpaEntity> findByYearWeekOrderByRanking(
            @Param("yearWeek") String yearWeek, Pageable pageable);

    long countByYearWeek(String yearWeek);
}
