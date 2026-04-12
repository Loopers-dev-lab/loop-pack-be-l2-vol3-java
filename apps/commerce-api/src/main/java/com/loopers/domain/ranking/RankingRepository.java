package com.loopers.domain.ranking;

import java.util.List;

public interface RankingRepository {

    List<ProductScore> getTopRankings(String dateKey, long offset, long size);

    Long getRank(String dateKey, Long productId);

    Double getScore(String dateKey, Long productId);

    record ProductScore(Long productId, double score) {}
}
