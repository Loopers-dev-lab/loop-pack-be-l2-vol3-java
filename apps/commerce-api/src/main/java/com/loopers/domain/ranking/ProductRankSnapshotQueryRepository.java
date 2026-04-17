package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ProductRankSnapshotQueryRepository {
    List<ProductRankSnapshot> findRankings(RankingType rankingType, LocalDate rankDate, int offset, int size);
    Optional<LocalDate> findLatestRankDate(RankingType rankingType);
}
