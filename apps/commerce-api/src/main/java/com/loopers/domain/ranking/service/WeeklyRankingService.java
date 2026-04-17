package com.loopers.domain.ranking.service;

import com.loopers.domain.ranking.model.RankingSlice;
import com.loopers.domain.ranking.repository.WeeklyRankingRepository;
import com.loopers.support.util.RankingPeriodKeyFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class WeeklyRankingService {

    private final WeeklyRankingRepository repository;

    public RankingSlice getRankings(LocalDate date, int page, int size) {
        int offset = (page - 1) * size;
        String periodKey = RankingPeriodKeyFactory.toWeeklyKey(date);
        return new RankingSlice(
                repository.getTopRankings(periodKey, offset, size),
                repository.getTotalCount(periodKey)
        );
    }
}
