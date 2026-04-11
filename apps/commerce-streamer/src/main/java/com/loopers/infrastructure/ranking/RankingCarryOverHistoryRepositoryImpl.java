package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingCarryOverHistory;
import com.loopers.domain.ranking.RankingCarryOverHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class RankingCarryOverHistoryRepositoryImpl implements RankingCarryOverHistoryRepository {

    private final RankingCarryOverHistoryJpaRepository jpaRepository;

    @Override
    public boolean existsByCarryOverDate(LocalDate date) {
        return jpaRepository.existsByCarryOverDate(date);
    }

    @Override
    public RankingCarryOverHistory save(RankingCarryOverHistory history) {
        return jpaRepository.save(history);
    }
}
