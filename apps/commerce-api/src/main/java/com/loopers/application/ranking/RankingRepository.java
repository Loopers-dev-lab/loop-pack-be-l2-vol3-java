package com.loopers.application.ranking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface RankingRepository {
    List<RankingProductView> findDailyPage(LocalDate metricDate, int page, int size);

    List<RankingProductView> findHourlyPage(LocalDateTime metricHour, int page, int size);

    RankingProductView findProductRank(LocalDate metricDate, UUID productId);
}
