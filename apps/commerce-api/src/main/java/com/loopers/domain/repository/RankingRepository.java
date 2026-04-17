package com.loopers.domain.repository;

import java.time.LocalDate;
import java.util.List;

public interface RankingRepository {

    List<RankedProduct> getTopRankings(LocalDate date, int offset, int size);

    long getTotalCount(LocalDate date);

    Long getRank(LocalDate date, Long productId);

    Double getScore(LocalDate date, Long productId);

    record RankedProduct(Long productId, double score, long rank) {}
}
