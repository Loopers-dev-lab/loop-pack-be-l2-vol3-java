package com.loopers.domain.ranking;

import java.util.List;

public interface MvRankingRepository {
    List<RankingEntry> getWeeklyTopN(int offset, int size);
    List<RankingEntry> getMonthlyTopN(int offset, int size);
}
