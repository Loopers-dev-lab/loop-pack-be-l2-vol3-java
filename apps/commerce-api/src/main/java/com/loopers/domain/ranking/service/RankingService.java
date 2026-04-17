package com.loopers.domain.ranking.service;

import com.loopers.domain.ranking.model.RankingEntry;
import com.loopers.domain.ranking.repository.RankingRepository;
import com.loopers.support.util.RankingPeriodKeyFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@Component
public class RankingService {

    private final RankingRepository rankingRepository;

    public List<RankingEntry> getTopRankings(LocalDate date, int page, int size) {
        int offset = (page - 1) * size;
        return rankingRepository.getTopRankings(RankingPeriodKeyFactory.toDailyKey(date), offset, size).stream()
                .map(entry -> new RankingEntry(entry.productId(), entry.score(), entry.rank()))
                .toList();
    }

    public long getTotalCount(LocalDate date) {
        return rankingRepository.getTotalCount(RankingPeriodKeyFactory.toDailyKey(date));
    }

    public RankingRepository.RankingEntry getProductRanking(String date, Long productId) {
        return rankingRepository.getProductRanking(date, productId);
    }

    public Long getProductRank(String date, Long productId) {
        RankingRepository.RankingEntry entry = rankingRepository.getProductRanking(date, productId);
        return entry != null ? entry.rank() : null;
    }
}
