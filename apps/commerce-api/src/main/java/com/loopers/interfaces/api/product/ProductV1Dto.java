package com.loopers.interfaces.api.product;

import com.loopers.domain.PageResult;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;
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
        public static ProductResponse from(Product product, Brand brand) {
            return new ProductResponse(
                product.getId(), product.getBrandId(), brand.getName(), product.getName(),
                product.getPrice().amount(), product.getLikeCount()
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
        public static ProductPageResponse from(PageResult<Product> result, Map<Long, Brand> brandMap) {
            List<ProductResponse> content = result.items().stream()
                .map(product -> {
                    Brand brand = brandMap.get(product.getBrandId());
                    if (brand == null) {
                        throw new CoreException(ErrorType.INTERNAL_ERROR,
                            "브랜드를 찾을 수 없습니다. brandId=" + product.getBrandId());
                    }
                    return ProductResponse.from(product, brand);
                })
                .toList();
            return new ProductPageResponse(content, result.page(), result.size(), result.totalElements(), result.totalPages());
        }
    }
}
