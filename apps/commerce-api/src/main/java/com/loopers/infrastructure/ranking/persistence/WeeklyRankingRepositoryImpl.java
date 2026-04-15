package com.loopers.infrastructure.ranking.persistence;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.ranking.ProductRankingWeekly;
import com.loopers.domain.ranking.WeeklyRankingRepository;

import lombok.RequiredArgsConstructor;

/**
 * 주간 랭킹 조회 구현체.
 *
 * <p>배치가 집계한 {@code mv_product_rank_weekly} 테이블에서 지정한 scoreDate의 랭킹을 조회한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class WeeklyRankingRepositoryImpl implements WeeklyRankingRepository {

    private final WeeklyRankingJpaRepository weeklyRankingJpaRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ProductRankingWeekly> readTopRanked(LocalDate scoreDate, int page, int size) {
        return weeklyRankingJpaRepository.findByScoreDateOrderByScoreDesc(scoreDate, PageRequest.of(page, size));
    }
}
