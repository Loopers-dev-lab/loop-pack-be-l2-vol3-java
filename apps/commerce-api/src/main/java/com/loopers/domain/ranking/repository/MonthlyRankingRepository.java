package com.loopers.domain.ranking.repository;

import com.loopers.domain.ranking.model.RankingEntry;

import java.util.List;

public interface MonthlyRankingRepository {
    List<RankingEntry> getTopRankings(String periodKey, int offset, int size);
    long getTotalCount(String periodKey);
}
