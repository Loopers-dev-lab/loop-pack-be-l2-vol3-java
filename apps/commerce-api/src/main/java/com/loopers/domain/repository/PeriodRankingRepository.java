package com.loopers.domain.repository;

import com.loopers.domain.repository.RankingRepository.RankedProduct;

import java.time.LocalDate;
import java.util.List;

public interface PeriodRankingRepository {

    List<RankedProduct> getWeeklyRankings(LocalDate date, int offset, int size);

    long getWeeklyTotalCount(LocalDate date);

    List<RankedProduct> getMonthlyRankings(LocalDate date, int offset, int size);

    long getMonthlyTotalCount(LocalDate date);
}
