package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class MvProductRankJpaRepository implements MvProductRankRepository {

    private final MvProductRankWeeklySpringDataRepository weeklyRepository;
    private final MvProductRankMonthlySpringDataRepository monthlyRepository;

    @Override
    public List<MvProductRank> findByPeriodKeyAndScope(String periodKey, String scope, Pageable pageable) {
        return switch (scope) {
            case "weekly" -> weeklyRepository.findByPeriodKeyOrderByRankingAsc(periodKey, pageable)
                .stream().map(r -> (MvProductRank) r).toList();
            case "monthly" -> monthlyRepository.findByPeriodKeyOrderByRankingAsc(periodKey, pageable)
                .stream().map(r -> (MvProductRank) r).toList();
            default -> throw new IllegalArgumentException("Invalid scope: " + scope);
        };
    }

    @Override
    public long countByPeriodKeyAndScope(String periodKey, String scope) {
        return switch (scope) {
            case "weekly" -> weeklyRepository.countByPeriodKey(periodKey);
            case "monthly" -> monthlyRepository.countByPeriodKey(periodKey);
            default -> throw new IllegalArgumentException("Invalid scope: " + scope);
        };
    }
}
