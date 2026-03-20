package com.loopers.application.product;

import com.loopers.domain.product.Brand;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.SortCondition;
import com.loopers.domain.product.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;

    @Cacheable(value = "product:detail", key = "#productId")
    public ProductDetailInfo getProductDetail(Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다."));
        Brand brand = product.getBrandId() != null
            ? brandRepository.findById(product.getBrandId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."))
            : null;
        return ProductDetailInfo.of(product, brand, product.getLikesCount());
    }

    @Cacheable(value = "product:list", key = "#sort.name()")
    public List<ProductListInfo> getProductList(SortCondition sort) {
        List<Product> products = productRepository.findAll(sort);
        if (products.isEmpty()) {
            return List.of();
        }

        List<Long> brandIds = products.stream()
            .map(Product::getBrandId)
            .filter(id -> id != null)
            .distinct()
            .toList();
        Map<Long, Brand> brandMap = brandIds.stream()
            .flatMap(id -> brandRepository.findById(id).stream())
            .collect(Collectors.toMap(Brand::getId, b -> b));

        return products.stream()
            .map(p -> {
                Brand brand = p.getBrandId() != null ? brandMap.get(p.getBrandId()) : null;
                return ProductListInfo.of(p, brand, p.getLikesCount());
            })
            .toList();
    }
}
