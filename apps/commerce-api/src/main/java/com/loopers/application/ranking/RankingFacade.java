package com.loopers.application.ranking;

import com.loopers.domain.brand.BrandModel;
import com.loopers.application.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.ranking.RankingPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class RankingFacade {

    private final RankingService rankingService;
    private final ProductRepository productRepository;
    private final BrandService brandService;

    public List<RankingWithProduct> getTopRankings(LocalDate date, int page, int size, RankingPeriod period) {
        List<RankingInfo> rankings = rankingService.getTopRankings(date, page, size, period);
        if (rankings.isEmpty()) return List.of();

        List<Long> productIds = rankings.stream().map(RankingInfo::productId).toList();
        Map<Long, ProductModel> productMap = productRepository.findAllByIdInAndDeletedAtIsNull(productIds)
            .stream()
            .collect(Collectors.toMap(ProductModel::getId, Function.identity()));

        List<Long> brandIds = productMap.values().stream()
            .map(ProductModel::getBrandId).distinct().toList();
        Map<Long, BrandModel> brandMap = brandService.getByIds(brandIds);

        return rankings.stream()
            .filter(r -> productMap.containsKey(r.productId()))
            .map(r -> {
                ProductModel product = productMap.get(r.productId());
                BrandModel brand = brandMap.get(product.getBrandId());
                String brandName = brand != null ? brand.getName() : null;
                return new RankingWithProduct(
                    r.rank(), r.score(),
                    product.getId(), product.getName(),
                    product.getPrice().value(), brandName
                );
            })
            .toList();
    }

    public Long getProductRank(Long productId) {
        return rankingService.getProductRank(LocalDate.now(), productId);
    }
}
