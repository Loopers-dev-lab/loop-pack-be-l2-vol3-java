package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingWeight;
import com.loopers.domain.ranking.RankingWeightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@RequiredArgsConstructor
@Repository
public class RankingWeightRepositoryImpl implements RankingWeightRepository {

    private final RankingWeightJpaRepository rankingWeightJpaRepository;

    @Override
    public Optional<RankingWeight> findByEventType(String eventType) {
        return rankingWeightJpaRepository.findByEventType(eventType);
    }

    @Override
    public RankingWeight save(RankingWeight rankingWeight) {
        return rankingWeightJpaRepository.save(rankingWeight);
    }
}
