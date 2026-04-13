package com.loopers.infrastructure.ranking.persistence;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.loopers.domain.ranking.ProductRankingWeekly;

public interface ProductRankingWeeklyJpaRepository extends JpaRepository<ProductRankingWeekly, Long> {

    @Modifying
    @Query(
            value = "INSERT INTO mv_product_rank_weekly (product_id, score_date, score) "
                    + "VALUES (:productId, :scoreDate, :score) "
                    + "ON DUPLICATE KEY UPDATE "
                    + "score = :score",
            nativeQuery = true
    )
    void upsert(
            @Param("productId") Long productId,
            @Param("scoreDate") LocalDate scoreDate,
            @Param("score") Double score
    );
}
