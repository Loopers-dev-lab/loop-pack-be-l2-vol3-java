package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductDetailInfo;
import com.loopers.application.product.ProductInfo;

public class ProductDto {

    public static record ListResponse(String name, String brand, int price, long likeCount) {
        public static ListResponse from(ProductInfo productInfo) {
            return new ListResponse(productInfo.name(), productInfo.brand(), productInfo.price(), productInfo.likeCount());
        }
    }

    public static record DetailResponse(String name, String description, String brand, int price, long likeCount, Long rank) {
        public static DetailResponse from(ProductDetailInfo info) {
            ProductInfo p = info.productInfo();
            return new DetailResponse(p.name(), p.description(), p.brand(), p.price(), p.likeCount(), info.rank());
        }
    }
}
