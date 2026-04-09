package com.loopers.application.ranking;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RankingRepository {
    List<RankingProductView> findTop(LocalDate metricDate, int limit);

    RankingProductView findProductRank(LocalDate metricDate, UUID productId);
}
