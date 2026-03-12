package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.common.Money;
import com.loopers.domain.product.Option;
import com.loopers.domain.product.Product;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

@Getter
@Builder
@Jacksonized
public class ProductInfo {
    private final Long productId;
    private final String productName;
    private final Money basePrice;
    private final boolean deleted;
    private final Long brandId;
    private final String brandName;
    private final long likeCount;
    private final boolean likedByUser;
    private final List<OptionInfo> options;

    @Getter
    @Builder
    @Jacksonized
    public static class OptionInfo {
        private final Long optionId;
        private final String optionName;
        private final Money additionalPrice;
        private final int stock;
        private final boolean soldOut;

        public static OptionInfo from(Option option) {
            return OptionInfo.builder()
                    .optionId(option.getId())
                    .optionName(option.getName())
                    .additionalPrice(option.getAdditionalPrice())
                    .stock(option.getStock())
                    .soldOut(option.isSoldOut())
                    .build();
        }
    }

    public ProductInfo withLikedByUser(boolean likedByUser) {
        return ProductInfo.builder()
                .productId(this.productId)
                .productName(this.productName)
                .basePrice(this.basePrice)
                .deleted(this.deleted)
                .brandId(this.brandId)
                .brandName(this.brandName)
                .likeCount(this.likeCount)
                .likedByUser(likedByUser)
                .options(this.options)
                .build();
    }

    public static ProductInfo of(Product product, Brand brand, List<Option> options, long likeCount, boolean likedByUser) {
        return ProductInfo.builder()
                .productId(product.getId())
                .productName(product.getName())
                .basePrice(product.getBasePrice())
                .deleted(product.isDeleted())
                .brandId(brand.getId())
                .brandName(brand.getName())
                .likeCount(likeCount)
                .likedByUser(likedByUser)
                .options(options.stream().map(OptionInfo::from).toList())
                .build();
    }
}
