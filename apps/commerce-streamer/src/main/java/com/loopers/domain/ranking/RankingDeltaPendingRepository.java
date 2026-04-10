package com.loopers.domain.ranking;

import java.util.List;

public interface RankingDeltaPendingRepository {

    RankingDeltaPending save(RankingDeltaPending delta);

    List<RankingDeltaPending> findPendingByEventIds(List<String> eventIds);

    void markAsFlushed(List<Long> ids);
}
