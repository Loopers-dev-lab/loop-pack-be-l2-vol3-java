package com.loopers.infrastructure.ranking.mv;

import com.loopers.domain.ranking.RankingMvReadRepository;
import com.loopers.domain.ranking.RankingMvTableRow;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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
     * 주간 랭킹 MV 최대 버전을 조회한다.
     *
     * @param periodKey 기간 키
     * @return 주간 랭킹 MV 최대 버전
     */
    @Override
    public Optional<Integer> findMaxVersionForWeekly(String periodKey) {
        return Optional.ofNullable(weeklyJpaRepository.findMaxVersionByPeriodKey(periodKey));
    }

    @Override
    public Optional<Integer> findMaxVersionForMonthly(String periodKey) {
        return Optional.ofNullable(monthlyJpaRepository.findMaxVersionByPeriodKey(periodKey));
    }

    /**
     * 주간 랭킹 MV를 조회한다.
     *
     * @param periodKey 기간 키
     * @param version 버전
     * @return 주간 랭킹 MV
     */
    @Override
    public List<RankingMvTableRow> findWeeklyByPeriodKeyAndVersionOrdered(String periodKey, int version) {
        return weeklyJpaRepository.findByPeriodKeyAndVersionOrderByRankValueAsc(periodKey, version).stream()
                .map(e -> new RankingMvTableRow(e.getRankValue(), e.getProductId(), e.getScore()))
                .toList();
    }

    /**
     * 월간 랭킹 MV를 조회한다.
     *
     * @param periodKey 기간 키
     * @param version 버전
     * @return 월간 랭킹 MV
     */
    @Override
    public List<RankingMvTableRow> findMonthlyByPeriodKeyAndVersionOrdered(String periodKey, int version) {
        return monthlyJpaRepository.findByPeriodKeyAndVersionOrderByRankValueAsc(periodKey, version).stream()
                .map(e -> new RankingMvTableRow(e.getRankValue(), e.getProductId(), e.getScore()))
                .toList();
    }
}
