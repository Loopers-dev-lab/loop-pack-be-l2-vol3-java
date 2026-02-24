package com.loopers.application.product;

import com.loopers.application.brand.BrandAppService;
import com.loopers.application.like.LikeAppService;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Option;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductSortCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ProductFacade {
    private final ProductAppService productAppService;
    private final BrandAppService brandAppService;
    private final LikeAppService likeAppService;

    public ProductInfo getProductDetail(Long productId, Long userId) {
        Product product = productAppService.getById(productId);
        Brand brand = brandAppService.getById(product.getBrandId());
        List<Option> options = productAppService.getOptionsByProductId(productId);
        long likeCount = likeAppService.countByProductId(productId);
        boolean likedByUser = userId != null && likeAppService.isLikedByUser(userId, productId);

        return ProductInfo.of(product, brand, options, likeCount, likedByUser);
    }

    public List<ProductInfo> getProductList(ProductSortCondition condition, Long userId) {
        List<Product> products = productAppService.getProducts(condition);

        if (products.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = products.stream().map(Product::getId).toList();
        List<Long> brandIds = products.stream().map(Product::getBrandId).distinct().toList();

        Map<Long, Brand> brandMap = brandAppService.getByIds(brandIds);
        Map<Long, List<Option>> optionMap = productAppService.getOptionsByProductIds(productIds);
        Map<Long, Long> likeCountMap = likeAppService.countByProductIds(productIds);
        Set<Long> likedProductIds = userId != null
                ? likeAppService.getLikedProductIds(userId, productIds)
                : Set.of();

        return products.stream()
                .map(product -> ProductInfo.of(
                        product,
                        brandMap.get(product.getBrandId()),
                        optionMap.getOrDefault(product.getId(), List.of()),
                        likeCountMap.getOrDefault(product.getId(), 0L),
                        likedProductIds.contains(product.getId())
                ))
                .toList();
    }
}
