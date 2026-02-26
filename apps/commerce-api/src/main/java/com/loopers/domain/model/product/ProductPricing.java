package com.loopers.domain.model.product;

import lombok.Getter;

@Getter
public class ProductPricing {

    private final Price price;
    private final Price salePrice;

    private ProductPricing(Price price, Price salePrice) {
        if (price == null) {
            throw new IllegalArgumentException("상품 가격은 필수입니다.");
        }
        this.price = price;
        this.salePrice = salePrice;
    }

    public static ProductPricing of(Price price, Price salePrice) {
        return new ProductPricing(price, salePrice);
    }

    public boolean isOnSale() {
        return this.salePrice != null;
    }

    public int getDiscountRate() {
        if (!isOnSale()) return 0;
        return calculateDiscountRate(price.getValue(), salePrice.getValue());
    }

    public static int calculateDiscountRate(int price, Integer salePrice) {
        if (salePrice == null) return 0;
        return (price - salePrice) * 100 / price;
    }
}
