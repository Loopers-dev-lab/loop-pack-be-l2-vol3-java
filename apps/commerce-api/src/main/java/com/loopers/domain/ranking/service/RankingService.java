package com.loopers.domain.ranking.service;

import com.loopers.domain.ranking.repository.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class RankingService {

    private final RankingRepository rankingRepository;

    public List<RankingRepository.RankingEntry> getTopRankings(String date, int page, int size) {
        int offset = (page - 1) * size;
        return rankingRepository.getTopRankings(date, offset, size);
    }

    public long getTotalCount(String date) {
        return rankingRepository.getTotalCount(date);
    }

    public RankingRepository.RankingEntry getProductRanking(String date, Long productId) {
        return rankingRepository.getProductRanking(date, productId);
    }

    public Long getProductRank(String date, Long productId) {
        RankingRepository.RankingEntry entry = rankingRepository.getProductRanking(date, productId);
        return entry != null ? entry.rank() : null;
    }
}
