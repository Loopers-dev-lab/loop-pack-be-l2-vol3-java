package com.loopers.application.product.cache;

import com.loopers.application.product.PublicProductDetailCacheApplicationService;
import com.loopers.application.product.view.ProductView;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Aspect
@Component
public class PublicProductDetailCacheAspect {

    private final PublicProductDetailCacheApplicationService publicProductDetailCacheApplicationService;

    public PublicProductDetailCacheAspect(PublicProductDetailCacheApplicationService publicProductDetailCacheApplicationService) {
        this.publicProductDetailCacheApplicationService = publicProductDetailCacheApplicationService;
    }

    @Around("@annotation(com.loopers.application.product.cache.CachedPublicProductDetail) && args(productId)")
    public Object cache(ProceedingJoinPoint joinPoint, UUID productId) throws Throwable {
        if (!publicProductDetailCacheApplicationService.enabled()) {
            return joinPoint.proceed();
        }

        Optional<ProductView> cached = publicProductDetailCacheApplicationService.find(productId);
        if (cached.isPresent()) {
            return cached.get();
        }

        ProductView resolved = (ProductView) joinPoint.proceed();
        publicProductDetailCacheApplicationService.save(productId, resolved);
        return resolved;
    }
}
