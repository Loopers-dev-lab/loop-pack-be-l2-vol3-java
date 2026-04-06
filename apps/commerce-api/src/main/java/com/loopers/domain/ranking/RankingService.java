package com.loopers.domain.ranking;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class RankingService {

    private final RankingRepository rankingRepository;

    public List<RankingRepository.ProductScore> getTopRankings(String dateKey, int page, int size) {
        long offset = (long) (page - 1) * size;
        return rankingRepository.getTopRankings(dateKey, offset, size);
    }

    public Long getRankForProduct(String dateKey, Long productId) {
        return rankingRepository.getRank(dateKey, productId);
    }

    public Double getScoreForProduct(String dateKey, Long productId) {
        return rankingRepository.getScore(dateKey, productId);
    }
}
