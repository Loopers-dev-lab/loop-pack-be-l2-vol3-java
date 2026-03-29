package com.loopers.application.product;

import com.loopers.application.brand.BrandAppService;
import com.loopers.application.like.LikeAppService;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.event.ProductViewedEvent;
import com.loopers.domain.product.Option;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductSortCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ProductFacade {
    private final ProductAppService productAppService;
    private final BrandAppService brandAppService;
    private final LikeAppService likeAppService;
    private final ApplicationEventPublisher eventPublisher;

    public ProductInfo getProductDetail(Long productId, Long userId) {
        CachedProductDetail detail = productAppService.getProductDetailCached(productId);
        Brand brand = brandAppService.getById(detail.getBrandId());
        boolean likedByUser = userId != null && likeAppService.isLikedByUser(userId, productId);

        eventPublisher.publishEvent(new ProductViewedEvent(productId, userId, ZonedDateTime.now()));

        return toProductInfo(detail, brand, likedByUser);
    }

    public Page<ProductInfo> getProductsByBrand(Long brandId, int page, int size) {
        CachedBrandProductPage cached = productAppService.getProductsByBrandIdCached(brandId, page, size);
        Brand brand = brandAppService.getById(brandId);
        List<ProductInfo> content = cached.getContent().stream()
                .map(summary -> toProductInfo(summary, brand))
                .toList();
        return new PageImpl<>(content, PageRequest.of(page, size), cached.getTotalElements());
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

    private ProductInfo toProductInfo(CachedProductDetail detail, Brand brand, boolean likedByUser) {
        return ProductInfo.builder()
                .productId(detail.getProductId())
                .productName(detail.getProductName())
                .basePrice(detail.getBasePrice())
                .deleted(detail.isDeleted())
                .brandId(brand.getId())
                .brandName(brand.getName())
                .likeCount(detail.getLikeCount())
                .likedByUser(likedByUser)
                .options(detail.getOptions())
                .build();
    }

    private ProductInfo toProductInfo(CachedBrandProductPage.ProductSummary summary, Brand brand) {
        return ProductInfo.builder()
                .productId(summary.getProductId())
                .productName(summary.getProductName())
                .basePrice(summary.getBasePrice())
                .deleted(summary.isDeleted())
                .brandId(brand.getId())
                .brandName(brand.getName())
                .likeCount(summary.getLikeCount())
                .likedByUser(false)
                .options(List.of())
                .build();
    }
}
