package com.loopers.infrastructure.ranking.repository;

import com.loopers.domain.ranking.model.RankingEntry;
import com.loopers.domain.ranking.repository.WeeklyRankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class WeeklyRankingRepositoryImpl implements WeeklyRankingRepository {

    private final WeeklyRankingJpaRepository repository;

    @Override
    public List<RankingEntry> getTopRankings(String periodKey, int offset, int size) {
        int page = offset / size;
        return repository.findByPeriodKeyOrderByRankNoAsc(periodKey, PageRequest.of(page, size)).stream()
                .map(entity -> new RankingEntry(entity.getProductId(), entity.getScore(), entity.getRankNo()))
                .toList();
    }

    @Override
    public long getTotalCount(String periodKey) {
        return repository.countByPeriodKey(periodKey);
    }
}
