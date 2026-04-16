package com.loopers.domain.ranking;

import com.loopers.domain.ranking.ProductRankWeeklyModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WeeklyRankingRepository {

    Page<ProductRankWeeklyModel> findByYearWeek(String yearWeek, Pageable pageable);
}
