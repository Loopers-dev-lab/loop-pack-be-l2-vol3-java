package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRankMonthly;
import com.loopers.domain.ranking.MvProductRankWeekly;
import com.loopers.domain.ranking.ProductRankMvRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ProductRankMvRepositoryImpl implements ProductRankMvRepository {

    private final MvProductRankWeeklyJpaRepository weeklyJpaRepository;
    private final MvProductRankMonthlyJpaRepository monthlyJpaRepository;

    @Override
    public List<MvProductRankWeekly> findWeeklyRankings(String yearWeek, int page, int size) {
        return weeklyJpaRepository.findByYearWeekOrderByRankingAsc(yearWeek, PageRequest.of(page, size));
    }

    @Override
    public List<MvProductRankMonthly> findMonthlyRankings(String yearMonth, int page, int size) {
        return monthlyJpaRepository.findByYearMonthOrderByRankingAsc(yearMonth, PageRequest.of(page, size));
    }

    @Override
    public void saveAllWeekly(List<MvProductRankWeekly> rankings) {
        weeklyJpaRepository.saveAll(rankings);
    }

    @Override
    public void saveAllMonthly(List<MvProductRankMonthly> rankings) {
        monthlyJpaRepository.saveAll(rankings);
    }

    @Override
    public void deleteWeeklyByYearWeek(String yearWeek) {
        List<MvProductRankWeekly> existing = weeklyJpaRepository.findByYearWeekOrderByRankingAsc(yearWeek);
        weeklyJpaRepository.deleteAll(existing);
    }

    @Override
    public void deleteMonthlyByYearMonth(String yearMonth) {
        List<MvProductRankMonthly> existing = monthlyJpaRepository.findByYearMonthOrderByRankingAsc(yearMonth);
        monthlyJpaRepository.deleteAll(existing);
    }
}
