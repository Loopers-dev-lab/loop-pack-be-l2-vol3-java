package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;

public interface ProductDailySignalRepository {

    void upsertViewCount(Long productDbId, LocalDate date, long delta);

    void upsertLikeCount(Long productDbId, LocalDate date, long delta);

    void upsertOrderAmount(Long productDbId, LocalDate date, double amount);

    List<ProductDailySignalModel> findBySignalDate(LocalDate date);
}
