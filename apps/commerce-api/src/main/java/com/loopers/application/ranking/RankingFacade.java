package com.loopers.application.ranking;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingInfo;
import com.loopers.domain.ranking.RankingKeyGenerator;
import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@Service
public class RankingFacade {
    private final RankingRepository rankingRepository;
    private final ProductFacade productFacade;

    public RankingPageResult getRankings(LocalDate date, int page, int size) {
        String key = RankingKeyGenerator.keyOf(date);
        int offset = (page - 1) * size;
        List<RankingEntry> entries = rankingRepository.getTopRankings(key, offset, size);

        List<RankingProductInfo> items = entries.stream()
                .map(entry -> {
                    ProductInfo product = productFacade.getActiveProduct(entry.productId());
                    return RankingProductInfo.of(product, entry.rank() + 1, entry.score());
                })
                .toList();

        return new RankingPageResult(items, page, size);
    }

    public RankingInfo getProductRank(Long productId) {
        String key = RankingKeyGenerator.todayKey();
        Long rank = rankingRepository.getRank(key, productId);
        if (rank == null) {
            return null;
        }
        Double score = rankingRepository.getScore(key, productId);
        return new RankingInfo(rank + 1, score);
    }
}
