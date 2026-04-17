package com.loopers.infrastructure.product;

import com.loopers.application.product.ProductCachePort;
import com.loopers.interfaces.api.product.ProductDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class MultiLayerProductCacheAdapter implements ProductCachePort {

    private final ProductCachePort l1Cache;
    private final ProductCachePort l2Cache;

    public MultiLayerProductCacheAdapter(
        @Qualifier("caffeineProductCacheAdapter") ProductCachePort l1Cache,
        @Qualifier("redisProductCacheAdapter") ProductCachePort l2Cache
    ) {
        this.l1Cache = l1Cache;
        this.l2Cache = l2Cache;
    }

    // ── 상품 상세 캐시 ──

    @Override
    public ProductDto.ProductResponse getProductDetail(Long productId) {
        ProductDto.ProductResponse cached = l1Cache.getProductDetail(productId);
        if (cached != null) {
            return cached;
        }

        cached = l2Cache.getProductDetail(productId);
        if (cached != null) {
            l1Cache.putProductDetail(productId, cached);
        }
        return cached;
    }

    @Override
    public void putProductDetail(Long productId, ProductDto.ProductResponse response) {
        l2Cache.putProductDetail(productId, response);
        l1Cache.putProductDetail(productId, response);
    }

    @Override
    public void evictProductDetail(Long productId) {
        l1Cache.evictProductDetail(productId);
        l2Cache.evictProductDetail(productId);
    }

    // ── 상품 목록 캐시 ──

    @Override
    public ProductDto.PagedProductResponse getProductList(Long brandId, String sort, int page, int size) {
        ProductDto.PagedProductResponse cached = l1Cache.getProductList(brandId, sort, page, size);
        if (cached != null) {
            return cached;
        }

        cached = l2Cache.getProductList(brandId, sort, page, size);
        if (cached != null) {
            l1Cache.putProductList(brandId, sort, page, size, cached);
        }
        return cached;
    }

    @Override
    public void putProductList(Long brandId, String sort, int page, int size, ProductDto.PagedProductResponse response) {
        l2Cache.putProductList(brandId, sort, page, size, response);
        l1Cache.putProductList(brandId, sort, page, size, response);
    }

    @Override
    public void evictProductList() {
        l1Cache.evictProductList();
        l2Cache.evictProductList();
    }
}
