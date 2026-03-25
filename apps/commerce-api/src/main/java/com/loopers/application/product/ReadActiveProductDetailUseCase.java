package com.loopers.application.product;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductEvent;
import com.loopers.domain.product.ProductEventPublisher;
import com.loopers.application.product.cache.ProductCacheReader;

import lombok.RequiredArgsConstructor;

/**
 * 사용자가 활성 상품의 상세 정보를 조회합니다.
 *
 * <p>상품, 브랜드, 좋아요 여부를 각각 조회한 뒤 하나의 상세 정보로 조합하여 반환합니다.
 * 조회 시 {@code ProductViewed} 이벤트를 발행하여 조회 수 집계에 활용한다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class ReadActiveProductDetailUseCase {

    private final ProductCacheReader productCacheReader;
    private final BrandService brandService;
    private final LikeService likeService;
    private final ProductEventPublisher productEventPublisher;

    /**
     * @param userId    사용자 ID (비로그인 시 null)
     * @param productId 상품 ID
     * @return 상품 상세 정보 (브랜드, 좋아요 정보 포함)
     */
    public ProductDetail execute(Long userId, Long productId) {
        Product product = productCacheReader.readActiveProduct(productId);
        Brand brand = brandService.getActiveBrand(product.getBrandId());
        boolean liked = likeService.isLiked(userId, productId);
        productEventPublisher.publishEvent(ProductEvent.ProductViewed.from(product));
        return ProductDetail.from(product, brand, liked);
    }
}
