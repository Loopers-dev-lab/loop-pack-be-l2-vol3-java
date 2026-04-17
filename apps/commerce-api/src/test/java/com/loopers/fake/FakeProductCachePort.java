package com.loopers.fake;

import com.loopers.application.product.ProductCachePort;
import com.loopers.interfaces.api.product.ProductDto;

public class FakeProductCachePort implements ProductCachePort {

    @Override
    public ProductDto.ProductResponse getProductDetail(Long productId) {
        return null;
    }

    @Override
    public void putProductDetail(Long productId, ProductDto.ProductResponse response) {
        // no-op
    }

    @Override
    public void evictProductDetail(Long productId) {
        // no-op
    }

    @Override
    public ProductDto.PagedProductResponse getProductList(Long brandId, String sort, int page, int size) {
        return null;
    }

    @Override
    public void putProductList(Long brandId, String sort, int page, int size, ProductDto.PagedProductResponse response) {
        // no-op
    }

    @Override
    public void evictProductList() {
        // no-op
    }
}
