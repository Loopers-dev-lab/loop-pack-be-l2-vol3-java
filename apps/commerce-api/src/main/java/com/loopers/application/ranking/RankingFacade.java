package com.loopers.application.ranking;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.ranking.RankedProduct;
import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RankingFacade {

    private final RankingRepository rankingRepository;
    private final ProductService productService;
    private final BrandService brandService;

    public RankingPageInfo getRankings(String date, int page, int size) {
        int offset = (page - 1) * size;
        List<RankedProduct> ranked = rankingRepository.findTopN(date, offset, size);

        if (ranked.isEmpty()) {
            return new RankingPageInfo(date, Collections.emptyList(), 0, page, size);
        }

        List<Long> productIds = ranked.stream().map(RankedProduct::productId).toList();
        List<Product> products = productService.getByIds(productIds);
        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        List<Long> brandIds = products.stream().map(Product::getBrandId).distinct().toList();
        Map<Long, Brand> brandMap = brandService.getByIds(brandIds).stream()
                .collect(Collectors.toMap(Brand::getId, b -> b));

        long rank = offset + 1;
        List<RankingInfo> items = new java.util.ArrayList<>();
        for (RankedProduct rp : ranked) {
            Product product = productMap.get(rp.productId());
            if (product == null) {
                rank++;
                continue;
            }
            Brand brand = brandMap.get(product.getBrandId());
            String brandName = brand != null ? brand.getName() : null;
            items.add(RankingInfo.of(rank++, rp.score(), product, brandName));
        }

        long totalSize = rankingRepository.getTotalSize(date);
        return new RankingPageInfo(date, items, totalSize, page, size);
    }

    public Long getProductRank(String date, Long productId) {
        Long rank = rankingRepository.findRank(date, productId);
        return rank != null ? rank + 1 : null;
    }
}
