package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.ProductRankMonthly;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRankMonthlyJpaRepository extends JpaRepository<ProductRankMonthly, Long> {
}
