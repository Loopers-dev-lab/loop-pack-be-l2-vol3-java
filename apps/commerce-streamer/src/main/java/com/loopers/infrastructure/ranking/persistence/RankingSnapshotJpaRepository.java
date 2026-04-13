package com.loopers.infrastructure.ranking.persistence;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.loopers.domain.ranking.RankingSnapshot;

public interface RankingSnapshotJpaRepository extends JpaRepository<RankingSnapshot, Long> {

    @Modifying
    @Query(
            value = "INSERT INTO ranking_snapshot (product_id, score_hour, score, updated_at) "
                    + "VALUES (:productId, :scoreHour, :score, NOW()) "
                    + "ON DUPLICATE KEY UPDATE "
                    + "score = :score, updated_at = NOW()",
            nativeQuery = true
    )
    void upsert(
            @Param("productId") Long productId,
            @Param("scoreHour") LocalDateTime scoreHour,
            @Param("score") Double score
    );
}
