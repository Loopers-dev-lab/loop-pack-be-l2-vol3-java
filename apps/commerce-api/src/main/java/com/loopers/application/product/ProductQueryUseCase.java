package com.loopers.application.product;

import com.loopers.domain.model.common.PageResult;

public interface ProductQueryUseCase {

    ProductDetailInfo getProduct(Long productId);

    PageResult<ProductSummaryInfo> getProducts(Long brandId, String sort, int page, int size);

    record ProductDetailInfo(
            Long id,
            Long brandId,
            String brandName,
            String name,
            int price,
            Integer salePrice,
            boolean onSale,
            int stock,
            int likeCount,
            String description
    ) {}

    record ProductSummaryInfo(
            Long id,
            Long brandId,
            String brandName,
            String name,
            int price,
            Integer salePrice,
            boolean onSale,
            int likeCount
    ) {}
}
