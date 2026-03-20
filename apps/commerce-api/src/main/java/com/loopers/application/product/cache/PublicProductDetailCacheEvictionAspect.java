package com.loopers.application.product.cache;

import com.loopers.application.product.PublicProductDetailCacheApplicationService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
public class PublicProductDetailCacheEvictionAspect {

    private final PublicProductDetailCacheApplicationService publicProductDetailCacheApplicationService;

    @AfterReturning("@annotation(com.loopers.application.product.cache.EvictPublicProductDetailCache) && args(productId,..)")
    public void evict(UUID productId) {
        publicProductDetailCacheApplicationService.evict(productId);
    }
}
