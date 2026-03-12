package com.loopers.application.product;

import com.loopers.interfaces.api.product.ProductDto;

public interface ProductCachePort {

    // ── 상품 상세 캐시 ──

    ProductDto.ProductResponse getProductDetail(Long productId);

    void putProductDetail(Long productId, ProductDto.ProductResponse response);

    void evictProductDetail(Long productId);

    // ── 상품 목록 캐시 ──

    ProductDto.PagedProductResponse getProductList(Long brandId, String sort, int page, int size);

    void putProductList(Long brandId, String sort, int page, int size, ProductDto.PagedProductResponse response);

    void evictProductList();
}
