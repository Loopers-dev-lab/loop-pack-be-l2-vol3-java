package com.loopers.infrastructure.ranking.persistence;

import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.ranking.ProductRankingMonthly;
import com.loopers.domain.ranking.ProductRankingMonthlyRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ProductRankingMonthlyRepositoryImpl implements ProductRankingMonthlyRepository {

    private final ProductRankingMonthlyJpaRepository productRankingMonthlyJpaRepository;

    @Override
    @Transactional
    public void saveAll(List<ProductRankingMonthly> rankings) {
        rankings.forEach(r ->
                productRankingMonthlyJpaRepository.upsert(r.getProductId(), r.getScoreDate(), r.getScore())
        );
    }
}
