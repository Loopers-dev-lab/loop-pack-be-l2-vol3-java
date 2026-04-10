package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;

public interface ProductRankingScoreRepository {

    ProductRankingScoreModel save(ProductRankingScoreModel score);

    Optional<ProductRankingScoreModel> findByProductIdAndRankingDate(Long productId, LocalDate rankingDate);

    List<ProductRankingScoreModel> findAllByRankingDate(LocalDate rankingDate);

    List<ProductRankingScoreModel> findTopByRankingDateOrderByScoreDesc(LocalDate rankingDate, Pageable pageable);

    long countByRankingDate(LocalDate rankingDate);

    Optional<Long> findRankByProductIdAndRankingDate(Long productId, LocalDate rankingDate);
}
