package com.loopers.infrastructure.product;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.loopers.application.product.ProductInfo;
import com.loopers.infrastructure.product.ProductCacheManager.CachedPage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
public class ProductLocalCacheManager {

    private final Cache<String, ProductInfo> detailCache;
    private final Cache<String, CachedPage> listCache;

    public ProductLocalCacheManager() {
        this.detailCache = Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(Duration.ofMinutes(1))
                .recordStats()
                .build();

        this.listCache = Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(Duration.ofSeconds(30))
                .recordStats()
                .build();
    }

    // Command

    public void putDetail(Long productId, ProductInfo info) {
        detailCache.put(detailKey(productId), info);
    }

    public void putList(String cacheKey, CachedPage cachedPage) {
        listCache.put(cacheKey, cachedPage);
    }

    public void evictDetail(Long productId) {
        detailCache.invalidate(detailKey(productId));
    }

    public void evictAllDetails() {
        detailCache.invalidateAll();
    }

    public void evictAllLists() {
        listCache.invalidateAll();
    }

    // Query

    public Optional<ProductInfo> getDetail(Long productId) {
        return Optional.ofNullable(detailCache.getIfPresent(detailKey(productId)));
    }

    public Optional<CachedPage> getList(String cacheKey) {
        return Optional.ofNullable(listCache.getIfPresent(cacheKey));
    }

    public String getDetailStats() {
        return detailCache.stats().toString();
    }

    public String getListStats() {
        return listCache.stats().toString();
    }

    private String detailKey(Long productId) {
        return "detail:" + productId;
    }
}
