package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.ProductRankWeeklyModel;
import com.loopers.domain.ranking.WeeklyRankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class WeeklyRankingRepositoryImpl implements WeeklyRankingRepository {

    private final WeeklyRankingJpaRepository weeklyRankingJpaRepository;

    @Override
    public Page<ProductRankWeeklyModel> findByYearWeek(String yearWeek, Pageable pageable) {
        return weeklyRankingJpaRepository.findByYearWeekOrderByRankNumberAsc(yearWeek, pageable);
    }
}
