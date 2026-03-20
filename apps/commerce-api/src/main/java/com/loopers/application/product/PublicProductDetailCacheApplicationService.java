package com.loopers.application.product;

import com.loopers.application.product.cache.PublicProductDetailCacheProperties;
import com.loopers.application.product.cache.PublicProductDetailCacheRepository;
import com.loopers.application.product.cache.PublicProductDetailCacheTtlPolicy;
import com.loopers.application.product.view.ProductView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PublicProductDetailCacheApplicationService {

    private static final String CACHE_KEY_PREFIX = "product:public-detail:v1";

    private final PublicProductDetailCacheRepository publicProductDetailCacheRepository;
    private final PublicProductDetailCacheProperties publicProductDetailCacheProperties;
    private final PublicProductDetailCacheTtlPolicy publicProductDetailCacheTtlPolicy;

    @Transactional(readOnly = true)
    public boolean enabled() {
        return publicProductDetailCacheProperties.enabled();
    }

    @Transactional(readOnly = true)
    public Optional<ProductView> find(UUID productId) {
        return publicProductDetailCacheRepository.findByKey(buildCacheKey(productId));
    }

    @Transactional
    public void save(UUID productId, ProductView value) {
        publicProductDetailCacheRepository.save(
                buildCacheKey(productId),
                value,
                publicProductDetailCacheTtlPolicy.resolve()
        );
    }

    @Transactional
    public void evict(UUID productId) {
        publicProductDetailCacheRepository.evict(buildCacheKey(productId));
    }

    @Transactional(readOnly = true)
    public String buildCacheKey(UUID productId) {
        return CACHE_KEY_PREFIX + ":id=" + productId;
    }
}
