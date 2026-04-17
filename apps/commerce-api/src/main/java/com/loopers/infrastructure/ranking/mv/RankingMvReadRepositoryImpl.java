package com.loopers.infrastructure.ranking.mv;

import com.loopers.domain.ranking.RankingMvReadRepository;
import com.loopers.domain.ranking.RankingMvTableRow;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class RankingMvReadRepositoryImpl implements RankingMvReadRepository {

    private final MvProductRankWeeklyJpaRepository weeklyJpaRepository;
    private final MvProductRankMonthlyJpaRepository monthlyJpaRepository;

    public RankingMvReadRepositoryImpl(
            MvProductRankWeeklyJpaRepository weeklyJpaRepository,
            MvProductRankMonthlyJpaRepository monthlyJpaRepository) {
        this.weeklyJpaRepository = weeklyJpaRepository;
        this.monthlyJpaRepository = monthlyJpaRepository;
    }

    /**
     * 주간 랭킹 MV를 조회한다.
     *
     * @param periodKey 기간 키
     * @return 주간 랭킹 MV
     */
    @Override
    public List<RankingMvTableRow> findWeeklyByPeriodKeyOrdered(String periodKey) {
        return weeklyJpaRepository.findByPeriodKeyOrderByRankValueAsc(periodKey).stream()
                .map(e -> new RankingMvTableRow(e.getRankValue(), e.getProductId(), e.getScore()))
                .toList();
    }

    /**
     * 월간 랭킹 MV를 조회한다.
     *
     * @param periodKey 기간 키
     * @return 월간 랭킹 MV
     */
    @Override
    public List<RankingMvTableRow> findMonthlyByPeriodKeyOrdered(String periodKey) {
        return monthlyJpaRepository.findByPeriodKeyOrderByRankValueAsc(periodKey).stream()
                .map(e -> new RankingMvTableRow(e.getRankValue(), e.getProductId(), e.getScore()))
                .toList();
    }
}
