package com.loopers.infrastructure.ranking.persistence;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.loopers.domain.ranking.RankingSnapshot;

public interface RankingSnapshotJpaRepository extends JpaRepository<RankingSnapshot, Long> {

    @Modifying
    @Query(
            value = "INSERT INTO ranking_snapshot (product_id, score_date, score, updated_at) "
                    + "VALUES (:productId, :scoreDate, :score, NOW()) "
                    + "ON DUPLICATE KEY UPDATE "
                    + "score = :score, updated_at = NOW()",
            nativeQuery = true
    )
    void upsert(
            @Param("productId") Long productId,
            @Param("scoreDate") LocalDate scoreDate,
            @Param("score") Double score
    );
}
