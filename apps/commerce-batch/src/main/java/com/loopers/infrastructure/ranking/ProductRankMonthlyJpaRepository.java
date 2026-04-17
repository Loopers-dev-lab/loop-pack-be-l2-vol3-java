package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.ProductRankMonthly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRankMonthlyJpaRepository extends JpaRepository<ProductRankMonthly, Long> {

    @Modifying
    @Query("DELETE FROM ProductRankMonthly r WHERE r.version = :version")
    void deleteByVersion(@Param("version") long version);
}
