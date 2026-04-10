package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingKeyGenerator;
import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Component
public class RankingService {

    private final RankingRepository rankingRepository;

    public List<RankingInfo> getTopRankings(LocalDate date, int page, int size) {
        String key = RankingKeyGenerator.dailyKey(date);
        int offset = (page - 1) * size;
        List<RankingEntry> entries = rankingRepository.getTopN(key, offset, size);
        List<RankingInfo> result = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            RankingEntry entry = entries.get(i);
            result.add(new RankingInfo(entry.productId(), entry.score(), offset + i + 1));
        }
        return result;
    }

    public Long getProductRank(LocalDate date, Long productId) {
        String key = RankingKeyGenerator.dailyKey(date);
        Long rank = rankingRepository.getRank(key, productId);
        return rank != null ? rank + 1 : null;
    }
}
