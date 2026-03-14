package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductReadModel;
import com.loopers.domain.PageResult;
import com.loopers.domain.brand.Brand;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.List;
import java.util.Map;

public class ProductV1Dto {

    public record ProductResponse(
        Long id,
        Long brandId,
        String brandName,
        String name,
        int price,
        int likeCount
    ) {
        public static ProductResponse from(ProductReadModel product, Brand brand) {
            return new ProductResponse(
                product.id(), product.brandId(), brand.getName(), product.name(),
                product.price(), product.likeCount()
            );
        }
    }

    public record ProductPageResponse(
        List<ProductResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        public static ProductPageResponse from(PageResult<ProductReadModel> result, Map<Long, Brand> brandMap) {
            List<ProductResponse> content = result.items().stream()
                .map(product -> {
                    Brand brand = brandMap.get(product.brandId());
                    if (brand == null) {
                        throw new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.");
                    }
                    return ProductResponse.from(product, brand);
                })
                .toList();
            return new ProductPageResponse(content, result.page(), result.size(), result.totalElements(), result.totalPages());
        }
    }
}
