package com.loopers.application.ranking;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class RankingFacade {

    private final RankingService rankingService;
    private final ProductService productService;

    @Transactional(readOnly = true)
    public List<RankingInfo> getRankings(String date, int page, int size) {
        List<RankingRepository.ProductScore> scores = rankingService.getTopRankings(date, page, size);

        if (scores.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = scores.stream()
                .map(RankingRepository.ProductScore::productId)
                .toList();

        Map<Long, Product> productMap = productService.getProductsByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        int startRank = (page - 1) * size + 1;
        List<RankingInfo> result = new ArrayList<>();

        for (int i = 0; i < scores.size(); i++) {
            RankingRepository.ProductScore ps = scores.get(i);
            Product product = productMap.get(ps.productId());
            if (product != null) {
                result.add(new RankingInfo(
                        startRank + i,
                        product.getId(),
                        product.getName(),
                        product.getPrice(),
                        product.getDescription(),
                        product.getLikeCount(),
                        ps.score()
                ));
            }
        }
        return result;
    }

    public Long getRankForProduct(Long productId, String date) {
        return rankingService.getRankForProduct(date, productId);
    }

    public ProductRankInfo getProductRankInfo(Long productId, String date) {
        Long rank = rankingService.getRankForProduct(date, productId);
        Double score = rankingService.getScoreForProduct(date, productId);
        return new ProductRankInfo(productId, rank, score, date);
    }
}
