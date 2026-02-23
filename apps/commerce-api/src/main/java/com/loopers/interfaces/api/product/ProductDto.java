package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;
import com.loopers.domain.product.ProductSortCondition;

import java.math.BigDecimal;
import java.util.List;

public class ProductDto {

    public record ProductListRequest(ProductSortCondition sort) {
        public ProductSortCondition toSortCondition() {
            return sort != null ? sort : ProductSortCondition.LATEST;
        }
    }

    public record ProductResponse(
            Long productId,
            String productName,
            BigDecimal basePrice,
            Long brandId,
            String brandName,
            long likeCount,
            boolean likedByUser,
            List<OptionResponse> options
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                    info.getProductId(),
                    info.getProductName(),
                    info.getBasePrice().getAmount(),
                    info.getBrandId(),
                    info.getBrandName(),
                    info.getLikeCount(),
                    info.isLikedByUser(),
                    info.getOptions().stream().map(OptionResponse::from).toList()
            );
        }
    }

    public record OptionResponse(
            Long optionId,
            String optionName,
            BigDecimal additionalPrice,
            int stock,
            boolean soldOut
    ) {
        public static OptionResponse from(ProductInfo.OptionInfo optionInfo) {
            return new OptionResponse(
                    optionInfo.getOptionId(),
                    optionInfo.getOptionName(),
                    optionInfo.getAdditionalPrice().getAmount(),
                    optionInfo.getStock(),
                    optionInfo.isSoldOut()
            );
        }
    }

    public record ProductListResponse(List<ProductResponse> products) {
        public static ProductListResponse from(List<ProductInfo> infoList) {
            return new ProductListResponse(
                    infoList.stream().map(ProductResponse::from).toList()
            );
        }
    }
}
