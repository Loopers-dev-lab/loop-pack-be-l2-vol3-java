package com.loopers.domain.ranking.mv;

import java.time.LocalDate;

public interface MvProductRankLast30dRepository {

    // Command
    MvProductRankLast30d save(MvProductRankLast30d entity);

    int deleteByAnchorDate(LocalDate anchorDate);

    // Query
    long countByAnchorDate(LocalDate anchorDate);
}
