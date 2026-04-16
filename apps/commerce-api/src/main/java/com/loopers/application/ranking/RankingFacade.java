package com.loopers.application.ranking;

import com.loopers.application.product.ProductInfo;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.infrastructure.ranking.RankingCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class RankingFacade {

    private final RankingCacheService rankingCacheService;
    private final ProductService productService;
    private final BrandService brandService;

    @Transactional(readOnly = true)
    public List<RankingInfo> getRankings(String date, int page, int size) {
        int offset = (page - 1) * size;
        List<String> productIdStrings = rankingCacheService.getProductIds(date, offset, size);

        if (productIdStrings.isEmpty()) {
            return List.of();
        }

        List<Long> ids = productIdStrings.stream().map(Long::valueOf).toList();
        List<Product> products = productService.getProductsByIds(ids);

        List<Long> brandIds = products.stream().map(Product::getBrandId).distinct().toList();
        Map<Long, Brand> brandMap = brandService.getBrandsByIds(brandIds).stream()
            .collect(Collectors.toMap(Brand::getId, b -> b));

        Map<Long, Product> productMap = products.stream()
            .collect(Collectors.toMap(Product::getId, p -> p));

        List<RankingInfo> result = new ArrayList<>();
        for (int i = 0; i < productIdStrings.size(); i++) {
            Long productId = Long.valueOf(productIdStrings.get(i));
            Product product = productMap.get(productId);
            if (product == null) {
                continue;
            }
            Brand brand = brandMap.get(product.getBrandId());
            if (brand == null) {
                continue;
            }
            ProductInfo productInfo = ProductInfo.from(product, brand);
            result.add(RankingInfo.of(offset + i + 1, productInfo));
        }
        return result;
    }

    public long getTotalCount(String date) {
        return rankingCacheService.getTotalCount(date);
    }
}
