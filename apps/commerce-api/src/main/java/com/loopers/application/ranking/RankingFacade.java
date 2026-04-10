package com.loopers.application.ranking;

import com.loopers.application.ranking.dto.RankingCriteria;
import com.loopers.application.ranking.dto.RankingResult;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.ranking.RankingScoreService;
import com.loopers.domain.ranking.dto.RankingInfo;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RankingFacade {

    private final RankingScoreService rankingScoreService;
    private final ProductService productService;
    private final BrandService brandService;

    @Transactional(readOnly = true)
    public RankingResult.RankingPage getTopRankings(RankingCriteria.Search criteria) {
        List<RankingInfo.RankedScore> rankedScores = rankingScoreService
                .getTopRankedByDate(criteria.date(), criteria.toPageable());
        long totalElements = rankingScoreService.countByDate(criteria.date());

        List<Long> productIds = rankedScores.stream()
                .map(RankingInfo.RankedScore::productId)
                .toList();
        Map<Long, ProductModel> productMap = productService
                .findAllByIds(productIds).stream()
                .collect(Collectors.toMap(ProductModel::getId, Function.identity()));

        List<Long> brandIds = productMap.values().stream()
                .map(ProductModel::getBrandId)
                .distinct()
                .toList();
        Map<Long, String> brandNameMap = brandService.getNameMapByIds(brandIds);

        List<RankingResult.RankingEntry> items = rankedScores.stream()
                .filter(ranked -> productMap.containsKey(ranked.productId()))
                .map(ranked -> {
                    ProductModel product = productMap.get(ranked.productId());
                    return RankingResult.RankingEntry.of(
                            ranked, product, brandNameMap.get(product.getBrandId()));
                })
                .toList();

        return new RankingResult.RankingPage(
                criteria.date(), criteria.page(), criteria.size(), totalElements, items);
    }

    @Transactional(readOnly = true)
    public Optional<Long> getProductRank(Long productId, LocalDate date) {
        return rankingScoreService.getRankByProductIdAndDate(productId, date);
    }

    @Transactional
    public void carryOverScores(LocalDate targetDate, double carryOverRate) {
        rankingScoreService.carryOver(targetDate, carryOverRate);
    }
}
