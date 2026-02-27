package com.loopers.application.product;

import com.loopers.domain.product.Brand;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.SortCondition;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final LikeRepository likeRepository;

    public ProductDetailInfo getProductDetail(Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다."));
        Brand brand = product.getBrandId() != null
            ? brandRepository.findById(product.getBrandId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."))
            : null;
        long likeCount = likeRepository.countByProductId(productId);
        return ProductDetailInfo.of(product, brand, likeCount);
    }

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

        List<Long> productIds = products.stream().map(Product::getId).toList();
        Map<Long, Long> likeCountMap = likeRepository.countByProductIds(productIds);

        return products.stream()
            .map(p -> ProductListInfo.of(p, brandMap, likeCountMap))
            .toList();
    }
}
