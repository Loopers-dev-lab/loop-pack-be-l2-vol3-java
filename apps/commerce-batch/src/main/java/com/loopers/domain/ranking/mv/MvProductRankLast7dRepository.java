package com.loopers.domain.ranking.mv;

import java.time.LocalDate;

public interface MvProductRankLast7dRepository {

    // Command
    MvProductRankLast7d save(MvProductRankLast7d entity);

    int deleteByAnchorDate(LocalDate anchorDate);

    // Query
    long countByAnchorDate(LocalDate anchorDate);
}
