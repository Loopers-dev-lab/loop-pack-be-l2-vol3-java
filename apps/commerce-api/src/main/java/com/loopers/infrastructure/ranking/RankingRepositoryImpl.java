package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.ProductRankEntry;
import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@Repository
public class RankingRepositoryImpl implements RankingRepository {

    private final MvProductRankWeeklyJpaRepository weeklyJpaRepository;
    private final MvProductRankMonthlyJpaRepository monthlyJpaRepository;

    @Override
    public List<ProductRankEntry> findWeeklyRankings(LocalDate weekStartDate, int page, int size) {
        return weeklyJpaRepository
            .findByWeekStartDateOrderByRankPositionAsc(weekStartDate, PageRequest.of(page - 1, size))
            .stream()
            .map(e -> new ProductRankEntry(e.getProductId(), e.getTotalScore(), e.getRankPosition()))
            .toList();
    }

    @Override
    public List<ProductRankEntry> findMonthlyRankings(LocalDate monthStartDate, int page, int size) {
        return monthlyJpaRepository
            .findByMonthStartDateOrderByRankPositionAsc(monthStartDate, PageRequest.of(page - 1, size))
            .stream()
            .map(e -> new ProductRankEntry(e.getProductId(), e.getTotalScore(), e.getRankPosition()))
            .toList();
    }
}
