package com.loopers.application.ranking;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.application.product.ProductApplicationService;
import com.loopers.domain.product.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RankingQueryFacade {

    private final RankingApplicationService rankingApplicationService;
    private final ProductApplicationService productApplicationService;
    private final BrandApplicationService brandApplicationService;

    public List<TopRankingProductView> getTop(int limit) {
        List<RankingProductView> rankingViews = rankingApplicationService.getTop(limit);
        if (rankingViews.isEmpty()) {
            return List.of();
        }
        List<UUID> productIds = rankingViews.stream().map(RankingProductView::productId).toList();
        List<Product> products = productApplicationService.findAllByIds(productIds);
        Map<UUID, Product> productsById = products.stream().collect(Collectors.toMap(Product::id, Function.identity()));
        Map<UUID, String> brandNames = brandApplicationService.findNamesByIds(products.stream().map(Product::brandId).toList());
        return rankingViews.stream()
                .filter(rankingView -> productsById.containsKey(rankingView.productId()))
                .map(rankingView -> {
                    Product product = productsById.get(rankingView.productId());
                    return new TopRankingProductView(
                            product.id(),
                            product.name(),
                            product.price(),
                            product.brandId(),
                            brandNames.get(product.brandId()),
                            product.likeCount(),
                            rankingView.rank(),
                            rankingView.score()
                    );
                })
                .toList();
    }

    public RankingProductView getProductRank(UUID productId) {
        productApplicationService.get(productId);
        return rankingApplicationService.getProductRank(productId);
    }
}
