package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.ProductRankWeekly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRankWeeklyJpaRepository extends JpaRepository<ProductRankWeekly, Long> {

    @Modifying
    @Query("DELETE FROM ProductRankWeekly r WHERE r.version = :version")
    void deleteByVersion(@Param("version") long version);
}
