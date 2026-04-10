package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingDeltaPending;
import com.loopers.domain.ranking.RankingDeltaPendingRepository;
import com.loopers.domain.ranking.RankingDeltaPendingStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class RankingDeltaPendingRepositoryImpl implements RankingDeltaPendingRepository {

    private final RankingDeltaPendingJpaRepository jpaRepository;

    @Override
    public RankingDeltaPending save(RankingDeltaPending delta) {
        return jpaRepository.save(delta);
    }

    @Override
    public List<RankingDeltaPending> findPendingByEventIds(List<String> eventIds) {
        return jpaRepository.findAllByEventIdInAndStatus(eventIds, RankingDeltaPendingStatus.PENDING);
    }

    @Override
    public void markAsFlushed(List<Long> ids) {
        jpaRepository.markAsFlushed(ids);
    }
}
