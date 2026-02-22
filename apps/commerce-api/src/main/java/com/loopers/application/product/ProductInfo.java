package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.common.Money;
import com.loopers.domain.product.Product;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductInfo {
    private final Long productId;
    private final String productName;
    private final Money price;
    private final int stock;
    private final Long brandId;
    private final String brandName;
    private final long likeCount;
    private final boolean likedByUser;

    public static ProductInfo of(Product product, Brand brand, long likeCount, boolean likedByUser) {
        return ProductInfo.builder()
                .productId(product.getId())
                .productName(product.getName())
                .price(product.getPrice())
                .stock(product.getStock())
                .brandId(brand.getId())
                .brandName(brand.getName())
                .likeCount(likeCount)
                .likedByUser(likedByUser)
                .build();
    }
}
