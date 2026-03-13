package com.loopers.application.product;

import com.loopers.domain.common.Money;
import com.loopers.domain.product.Option;
import com.loopers.domain.product.Product;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Getter
@Builder
@Jacksonized
public class CachedProductDetail {
    private final Long productId;
    private final String productName;
    private final Money basePrice;
    private final boolean deleted;
    private final Long brandId;
    private final long likeCount;
    private final List<ProductInfo.OptionInfo> options;

    public static CachedProductDetail from(Product product, List<Option> options, long likeCount) {
        return CachedProductDetail.builder()
                .productId(product.getId())
                .productName(product.getName())
                .basePrice(product.getBasePrice())
                .deleted(product.isDeleted())
                .brandId(product.getBrandId())
                .likeCount(likeCount)
                .options(Optional.ofNullable(options).orElseGet(Collections::emptyList)
                        .stream().map(ProductInfo.OptionInfo::from).toList())
                .build();
    }
}
