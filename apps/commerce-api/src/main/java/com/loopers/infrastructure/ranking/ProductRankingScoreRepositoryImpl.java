package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.ProductRankingScoreModel;
import com.loopers.domain.ranking.ProductRankingScoreRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProductRankingScoreRepositoryImpl implements ProductRankingScoreRepository {

    private final ProductRankingScoreJpaRepository jpaRepository;
    private final ProductRankingScoreQueryRepository queryRepository;
    private final ProductRankingScoreRedisRepository redisRepository;

    @Override
    public List<ProductRankingScoreModel> findTopByRankingDateOrderByScoreDesc(
            LocalDate rankingDate, Pageable pageable) {
        return redisRepository.findTopByDate(rankingDate,
                pageable.getOffset(),
                pageable.getOffset() + pageable.getPageSize() - 1);
    }

    @Override
    public long countByRankingDate(LocalDate rankingDate) {
        return redisRepository.countByDate(rankingDate);
    }

    @Override
    public Optional<Long> findRankByProductIdAndRankingDate(
            Long productId, LocalDate rankingDate) {
        return redisRepository.findRankByProductId(productId, rankingDate);
    }

    @Override
    public ProductRankingScoreModel save(ProductRankingScoreModel score) {
        return jpaRepository.save(score);
    }

    @Override
    public Optional<ProductRankingScoreModel> findByProductIdAndRankingDate(
            Long productId, LocalDate rankingDate) {
        return jpaRepository.findByProductIdAndRankingDate(productId, rankingDate);
    }

    @Override
    public List<ProductRankingScoreModel> findAllByRankingDate(LocalDate rankingDate) {
        return queryRepository.findAllByRankingDate(rankingDate);
    }
}
