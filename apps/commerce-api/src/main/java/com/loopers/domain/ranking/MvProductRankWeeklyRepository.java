package com.loopers.domain.ranking;

import java.util.List;

public interface MvProductRankWeeklyRepository {
    List<MvProductRankWeekly> findTop(int page, int size);
}
