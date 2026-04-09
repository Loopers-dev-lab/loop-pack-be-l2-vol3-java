package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;

public interface RankingRepository {
    List<RankedProduct> getTopN(LocalDate date, int size, int page);
    Long getRank(Long productId, LocalDate date);
}
