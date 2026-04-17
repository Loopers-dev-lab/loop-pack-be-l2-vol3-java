package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvRankingRepository;
import com.loopers.domain.ranking.RankingEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class MvRankingRepositoryImpl implements MvRankingRepository {

    private final MvProductRankWeeklyJpaRepository weeklyJpaRepository;
    private final MvProductRankMonthlyJpaRepository monthlyJpaRepository;

    @Override
    public List<RankingEntry> getWeeklyTopN(int offset, int size) {
        var pageable = PageRequest.of(offset / Math.max(size, 1), size);
        return weeklyJpaRepository.findAllByOrderByRankingAsc(pageable).stream()
            .map(r -> new RankingEntry(r.getProductId(), r.getScore()))
            .toList();
    }

    @Override
    public List<RankingEntry> getMonthlyTopN(int offset, int size) {
        var pageable = PageRequest.of(offset / Math.max(size, 1), size);
        return monthlyJpaRepository.findAllByOrderByRankingAsc(pageable).stream()
            .map(r -> new RankingEntry(r.getProductId(), r.getScore()))
            .toList();
    }
}
