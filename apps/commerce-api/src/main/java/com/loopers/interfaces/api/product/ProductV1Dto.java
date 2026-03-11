package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductReadModel;
import com.loopers.domain.PageResult;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;

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
        public static ProductPageResponse from(PageResult<Product> result, Map<Long, Brand> brandMap) {
            List<ProductResponse> content = result.items().stream()
                .filter(product -> brandMap.containsKey(product.getBrandId()))
                .map(product -> ProductResponse.from(product, brandMap.get(product.getBrandId())))
                .toList();
            return new ProductPageResponse(content, result.page(), result.size(), result.totalElements(), result.totalPages());
        }
    }
}
