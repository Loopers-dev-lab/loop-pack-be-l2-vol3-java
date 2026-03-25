package com.loopers.application.like;

import com.loopers.application.cache.ProductCacheManager;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.BrandLike;
import com.loopers.domain.like.BrandLikeService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.like.ProductLike;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.common.event.ProductLikedEvent;
import com.loopers.infrastructure.outbox.OutboxEventService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 좋아요 Facade
 *
 * Like + BrandLike + Product + Brand 도메인 서비스를 조합하여
 * 좋아요 관련 유스케이스를 처리한다.
 */
@Component
public class LikeFacade {

    private final LikeService likeService;
    private final BrandLikeService brandLikeService;
    private final ProductService productService;
    private final BrandService brandService;
    private final ProductCacheManager productCacheManager;
    private final OutboxEventService outboxEventService;

    public LikeFacade(LikeService likeService, BrandLikeService brandLikeService,
                      ProductService productService, BrandService brandService,
                      ProductCacheManager productCacheManager,
                      OutboxEventService outboxEventService) {
        this.likeService = likeService;
        this.brandLikeService = brandLikeService;
        this.productService = productService;
        this.brandService = brandService;
        this.productCacheManager = productCacheManager;
        this.outboxEventService = outboxEventService;
    }

    /** 상품 좋아요 (상품 검증 → 좋아요 생성 → likeCount 증가 → 상세 캐시만 삭제) */
    @Transactional
    public LikeResult likeProduct(Long userId, Long productId) {
        Product product = productService.getDisplayableProduct(productId);
        likeService.like(userId, productId);
        productService.incrementLikeCount(productId);
        productCacheManager.registerDetailOnlyEvictAfterCommit(productId);

        // Outbox 저장 — 같은 TX (좋아요 집계 → catalog-events-v1)
        outboxEventService.save("PRODUCT", productId,
                "ProductLikedEvent", new ProductLikedEvent(userId, productId, true),
                "catalog-events-v1", String.valueOf(productId));

        return new LikeResult(product.getLikeCount() + 1);
    }

    /** 상품 좋아요 취소 (상품 존재 검증 → 좋아요 삭제 → likeCount 감소 → 상세 캐시만 삭제) */
    @Transactional
    public LikeResult unlikeProduct(Long userId, Long productId) {
        Product product = productService.getById(productId);
        likeService.unlike(userId, productId);
        productService.decrementLikeCount(productId);
        productCacheManager.registerDetailOnlyEvictAfterCommit(productId);

        // Outbox 저장 — 같은 TX (좋아요 취소 집계 → catalog-events-v1)
        outboxEventService.save("PRODUCT", productId,
                "ProductUnlikedEvent", new ProductLikedEvent(userId, productId, false),
                "catalog-events-v1", String.valueOf(productId));

        return new LikeResult(product.getLikeCount() - 1);
    }

    /** 브랜드 좋아요 (활성 브랜드 검증 → 좋아요 생성) */
    @Transactional
    public void likeBrand(Long userId, Long brandId) {
        brandService.getActiveBrand(brandId);
        brandLikeService.like(userId, brandId);
    }

    /** 브랜드 좋아요 취소 (브랜드 존재 검증 → 좋아요 삭제) */
    @Transactional
    public void unlikeBrand(Long userId, Long brandId) {
        brandService.getActiveBrand(brandId);
        brandLikeService.unlike(userId, brandId);
    }

    /** 내가 좋아요한 상품 목록 조회 */
    @Transactional(readOnly = true)
    public LikedProductListResult getMyLikedProducts(Long userId, int page, int size) {
        List<ProductLike> likes = likeService.getMyProductLikes(userId, page, size);
        long totalElements = likeService.countMyProductLikes(userId);
        int totalPages = (int) Math.ceil((double) totalElements / size);

        List<Long> productIds = likes.stream().map(ProductLike::getProductId).toList();
        Map<Long, Product> productMap = productService.getProductsByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<Long> brandIds = productMap.values().stream()
                .map(Product::getBrandId).distinct().toList();
        Map<Long, Brand> brandMap = brandService.getBrandsByIds(brandIds).stream()
                .collect(Collectors.toMap(Brand::getId, Function.identity()));

        List<LikedProductDetail> products = likes.stream()
                .filter(like -> productMap.containsKey(like.getProductId()))
                .map(like -> {
                    Product product = productMap.get(like.getProductId());
                    Brand brand = brandMap.get(product.getBrandId());
                    String brandName = brand != null ? brand.getName() : "";
                    return new LikedProductDetail(
                            product.getId(), product.getName(), product.getBasePrice(),
                            brandName, product.getLikeCount(), like.getCreatedAt());
                })
                .toList();

        return new LikedProductListResult(products, page, size, totalElements, totalPages);
    }

    /** 내가 좋아요한 브랜드 목록 조회 */
    @Transactional(readOnly = true)
    public LikedBrandListResult getMyLikedBrands(Long userId, int page, int size) {
        List<BrandLike> likes = brandLikeService.getMyBrandLikes(userId, page, size);
        long totalElements = brandLikeService.countMyBrandLikes(userId);
        int totalPages = (int) Math.ceil((double) totalElements / size);

        List<Long> brandIds = likes.stream().map(BrandLike::getBrandId).toList();
        Map<Long, Brand> brandMap = brandService.getBrandsByIds(brandIds).stream()
                .collect(Collectors.toMap(Brand::getId, Function.identity()));

        List<LikedBrandDetail> brands = likes.stream()
                .filter(like -> brandMap.containsKey(like.getBrandId()))
                .map(like -> {
                    Brand brand = brandMap.get(like.getBrandId());
                    return new LikedBrandDetail(
                            brand.getId(), brand.getName(), brand.getDescription(),
                            like.getCreatedAt());
                })
                .toList();

        return new LikedBrandListResult(brands, page, size, totalElements, totalPages);
    }

    public record LikeResult(int likeCount) {}

    public record LikedProductDetail(
            Long productId, String productName, int basePrice,
            String brandName, int likeCount, ZonedDateTime likedAt) {}

    public record LikedProductListResult(
            List<LikedProductDetail> products,
            int page, int size, long totalElements, int totalPages) {}

    public record LikedBrandDetail(
            Long brandId, String brandName, String description,
            ZonedDateTime likedAt) {}

    public record LikedBrandListResult(
            List<LikedBrandDetail> brands,
            int page, int size, long totalElements, int totalPages) {}
}
