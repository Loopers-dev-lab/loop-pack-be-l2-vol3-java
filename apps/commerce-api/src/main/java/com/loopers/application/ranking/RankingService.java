package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankEntry;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.infrastructure.ranking.RankingRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RankingService {

    private final RankingRedisRepository rankingRedisRepository;
    private final RankingKeyResolver keyResolver;

    // Query

    public List<RankEntry> getRankEntries(RankingPeriod period, LocalDate date, int page, int size) {
        String key = keyResolver.resolve(period, date);
        return rankingRedisRepository.getRankings(key, page, size);
    }

    public long getTotalCount(RankingPeriod period, LocalDate date) {
        String key = keyResolver.resolve(period, date);
        Long count = rankingRedisRepository.getTotalCount(key);
        return count == null ? 0 : count;
    }

    public Integer getProductRank(Long productId, RankingPeriod period, LocalDate date) {
        String key = keyResolver.resolve(period, date);
        return rankingRedisRepository.getRank(key, productId);
    }
}
