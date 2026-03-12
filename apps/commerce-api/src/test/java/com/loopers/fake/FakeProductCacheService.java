package com.loopers.fake;

import com.loopers.application.product.ProductCacheService;

public class FakeProductCacheService extends ProductCacheService {

    public FakeProductCacheService() {
        super(null, null, null);
    }

    @Override
    public com.loopers.interfaces.api.product.ProductDto.ProductResponse getProductDetail(Long productId) {
        return null;
    }

    @Override
    public void putProductDetail(Long productId, com.loopers.interfaces.api.product.ProductDto.ProductResponse response) {
        // no-op
    }

    @Override
    public void evictProductDetail(Long productId) {
        // no-op
    }

    @Override
    public com.loopers.interfaces.api.product.ProductDto.PagedProductResponse getProductList(Long brandId, String sort, int page, int size) {
        return null;
    }

    @Override
    public void putProductList(Long brandId, String sort, int page, int size, com.loopers.interfaces.api.product.ProductDto.PagedProductResponse response) {
        // no-op
    }

    @Override
    public void evictProductList() {
        // no-op
    }
}
