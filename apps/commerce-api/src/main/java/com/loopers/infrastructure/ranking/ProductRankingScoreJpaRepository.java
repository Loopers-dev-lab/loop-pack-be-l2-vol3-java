package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.ProductRankingScoreModel;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRankingScoreJpaRepository
        extends JpaRepository<ProductRankingScoreModel, Long> {

    Optional<ProductRankingScoreModel> findByProductIdAndRankingDate(
            Long productId, LocalDate rankingDate);

    long countByRankingDate(LocalDate rankingDate);
}
