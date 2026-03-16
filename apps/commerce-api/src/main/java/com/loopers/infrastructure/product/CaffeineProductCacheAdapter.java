package com.loopers.infrastructure.product;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.loopers.application.product.ProductCachePort;
import com.loopers.interfaces.api.product.ProductDto;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class CaffeineProductCacheAdapter implements ProductCachePort {

    private final Cache<String, ProductDto.ProductResponse> detailCache;
    private final Cache<String, ProductDto.PagedProductResponse> listCache;

    public CaffeineProductCacheAdapter() {
        this.detailCache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofSeconds(30))
            .build();
        this.listCache = Caffeine.newBuilder()
            .maximumSize(200)
            .expireAfterWrite(Duration.ofSeconds(15))
            .build();
    }

    @Override
    public ProductDto.ProductResponse getProductDetail(Long productId) {
        return detailCache.getIfPresent(detailKey(productId));
    }

    @Override
    public void putProductDetail(Long productId, ProductDto.ProductResponse response) {
        detailCache.put(detailKey(productId), response);
    }

    @Override
    public void evictProductDetail(Long productId) {
        detailCache.invalidate(detailKey(productId));
    }

    @Override
    public ProductDto.PagedProductResponse getProductList(Long brandId, String sort, int page, int size) {
        return listCache.getIfPresent(listKey(brandId, sort, page, size));
    }

    @Override
    public void putProductList(Long brandId, String sort, int page, int size, ProductDto.PagedProductResponse response) {
        listCache.put(listKey(brandId, sort, page, size), response);
    }

    @Override
    public void evictProductList() {
        listCache.invalidateAll();
    }

    private String detailKey(Long productId) {
        return "detail:" + productId;
    }

    private String listKey(Long brandId, String sort, int page, int size) {
        String brandPart = brandId != null ? String.valueOf(brandId) : "all";
        return "list:brand:" + brandPart + ":sort:" + sort + ":page:" + page + ":size:" + size;
    }
}
