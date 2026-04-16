package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MonthlyRankingRepository;
import com.loopers.domain.ranking.ProductRankMonthlyModel;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class MonthlyRankingRepositoryImpl implements MonthlyRankingRepository {

    private final MonthlyRankingJpaRepository monthlyRankingJpaRepository;

    @Override
    public Page<ProductRankMonthlyModel> findByYearMonth(String yearMonth, Pageable pageable) {
        return monthlyRankingJpaRepository.findByYearMonthOrderByRankNumberAsc(yearMonth, pageable);
    }
}
