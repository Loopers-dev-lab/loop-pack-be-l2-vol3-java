package com.loopers.application.product;

import com.loopers.domain.common.Money;
import com.loopers.domain.product.Product;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

@Getter
@Builder
@Jacksonized
public class CachedBrandProductPage {
    private final List<ProductSummary> content;
    private final long totalElements;

    @Getter
    @Builder
    @Jacksonized
    public static class ProductSummary {
        private final Long productId;
        private final String productName;
        private final Money basePrice;
        private final boolean deleted;
        private final long likeCount;

        public static ProductSummary from(Product product) {
            return ProductSummary.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .basePrice(product.getBasePrice())
                    .deleted(product.isDeleted())
                    .likeCount(product.getLikeCount())
                    .build();
        }
    }
}
