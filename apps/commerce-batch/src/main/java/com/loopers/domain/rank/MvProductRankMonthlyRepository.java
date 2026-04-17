package com.loopers.domain.rank;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MvProductRankMonthlyRepository {

    void upsertAll(List<? extends MvProductRankMonthly> rows);

    long countBySnapshotDate(LocalDate snapshotDate);

    Optional<MvProductRankMonthly> findBySnapshotDateAndProductId(LocalDate snapshotDate, Long productId);
}
