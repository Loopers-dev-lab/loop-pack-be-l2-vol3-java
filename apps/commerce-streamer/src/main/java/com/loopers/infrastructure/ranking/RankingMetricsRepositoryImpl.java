package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.DirtyEntry;
import com.loopers.domain.ranking.RankingMetricsRepository;
import com.loopers.domain.ranking.RankingMetricsSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@Repository
public class RankingMetricsRepositoryImpl implements RankingMetricsRepository {

    private final RankingMetricsJpaRepository rankingMetricsJpaRepository;

    @Override
    public void upsertViewCount(Long productId, LocalDate date, int hour, int viewCount) {
        rankingMetricsJpaRepository.upsertViewCount(productId, date, hour, viewCount);
    }

    @Override
    public void upsertLikeCount(Long productId, LocalDate date, int hour, int likeCount) {
        rankingMetricsJpaRepository.upsertLikeCount(productId, date, hour, likeCount);
    }

    @Override
    public void upsertOrderRevenue(Long productId, LocalDate date, int hour, BigDecimal revenue) {
        rankingMetricsJpaRepository.upsertOrderRevenue(productId, date, hour, revenue);
    }

    @Override
    public List<DirtyEntry> findDirtyEntries(LocalDate date) {
        return rankingMetricsJpaRepository.findDirtyEntries(date);
    }

    @Override
    public RankingMetricsSummary sumByProductIdAndDate(Long productId, LocalDate date) {
        return rankingMetricsJpaRepository.sumByProductIdAndDate(productId, date);
    }

    @Override
    public RankingMetricsSummary sumByProductIdAndDateAndHour(Long productId, LocalDate date, int hour) {
        return rankingMetricsJpaRepository.sumByProductIdAndDateAndHour(productId, date, hour);
    }

    @Override
    public void clearDirtyByHour(Long productId, LocalDate date, int hour) {
        rankingMetricsJpaRepository.clearDirtyByHour(productId, date, hour);
    }
}
