package com.loopers.domain.ranking;

import java.util.List;

public interface MvProductRankMonthlyRepository {
    List<MvProductRankMonthly> findTop(int page, int size);
}
