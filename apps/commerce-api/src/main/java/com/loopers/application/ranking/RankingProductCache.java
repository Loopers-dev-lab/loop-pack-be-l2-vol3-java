package com.loopers.application.ranking;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RankingProductCache {

    private final ProductRepository productRepository;

    @Cacheable(value = "rankingProductInfo", key = "#productDbId", unless = "#result == null")
    public CachedProductSnapshot findById(Long productDbId) {
        return productRepository.findById(productDbId)
                .map(CachedProductSnapshot::from)
                .orElse(null);
    }
}
