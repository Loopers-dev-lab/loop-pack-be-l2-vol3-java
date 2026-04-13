package com.loopers.infrastructure.ranking.persistence;

import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.ranking.ProductRankingWeekly;
import com.loopers.domain.ranking.ProductRankingWeeklyRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ProductRankingWeeklyRepositoryImpl implements ProductRankingWeeklyRepository {

    private final ProductRankingWeeklyJpaRepository productRankingWeeklyJpaRepository;

    @Override
    @Transactional
    public void saveAll(List<ProductRankingWeekly> rankings) {
        rankings.forEach(r ->
                productRankingWeeklyJpaRepository.upsert(r.getProductId(), r.getScoreDate(), r.getScore())
        );
    }
}
