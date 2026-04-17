package com.loopers.domain.rank;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MvProductRankWeeklyRepository {

    void upsertAll(List<? extends MvProductRankWeekly> rows);

    long countBySnapshotDate(LocalDate snapshotDate);

    Optional<MvProductRankWeekly> findBySnapshotDateAndProductId(LocalDate snapshotDate, Long productId);
}
