package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;

public interface MvProductRankMonthlyRepository {
    List<MvProductRankMonthly> findByDate(LocalDate date, int page, int size);
}
