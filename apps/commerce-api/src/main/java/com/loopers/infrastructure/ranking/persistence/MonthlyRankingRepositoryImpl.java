package com.loopers.infrastructure.ranking.persistence;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.ranking.MonthlyRankingRepository;
import com.loopers.domain.ranking.ProductRankingMonthly;

import lombok.RequiredArgsConstructor;

/**
 * 월간 랭킹 조회 구현체.
 *
 * <p>배치가 집계한 {@code mv_product_rank_monthly} 테이블에서 지정한 scoreDate의 랭킹을 조회한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class MonthlyRankingRepositoryImpl implements MonthlyRankingRepository {

    private final MonthlyRankingJpaRepository monthlyRankingJpaRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ProductRankingMonthly> readTopRanked(LocalDate scoreDate, int page, int size) {
        return monthlyRankingJpaRepository.findByScoreDateOrderByScoreDesc(scoreDate, PageRequest.of(page, size));
    }
}
