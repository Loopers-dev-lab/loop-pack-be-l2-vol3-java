package com.loopers.domain.ranking;

import java.util.Optional;

public interface RankingWeightRepository {

    Optional<RankingWeight> findByEventType(String eventType);

    RankingWeight save(RankingWeight rankingWeight);
}
