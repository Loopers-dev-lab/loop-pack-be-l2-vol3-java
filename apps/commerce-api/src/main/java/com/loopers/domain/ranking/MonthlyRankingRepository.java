package com.loopers.domain.ranking;

import com.loopers.domain.ranking.ProductRankMonthlyModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MonthlyRankingRepository {

    Page<ProductRankMonthlyModel> findByYearMonth(String yearMonth, Pageable pageable);
}
