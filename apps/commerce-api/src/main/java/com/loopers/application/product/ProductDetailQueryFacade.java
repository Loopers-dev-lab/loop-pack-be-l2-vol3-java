package com.loopers.application.product;

import com.loopers.application.product.view.ProductDetailView;
import com.loopers.application.product.view.ProductView;
import com.loopers.application.ranking.RankingApplicationService;
import com.loopers.application.ranking.RankingProductView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProductDetailQueryFacade {

    private final ProductQueryFacade productQueryFacade;
    private final RankingApplicationService rankingApplicationService;

    public ProductDetailView get(UUID productId) {
        ProductView productView = productQueryFacade.get(productId);
        RankingProductView rankingProductView = rankingApplicationService.getProductRank(productId);
        return ProductDetailView.from(productView, rankingProductView.rank());
    }
}
