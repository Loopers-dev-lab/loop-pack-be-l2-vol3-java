package com.loopers.application.product;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.like.LikeService;
import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;

import lombok.RequiredArgsConstructor;

/**
 * 사용자가 활성 상품의 상세 정보를 조회합니다.
 *
 * <p>상품, 브랜드, 좋아요 여부를 각각 조회한 뒤 하나의 상세 정보로 조합하여 반환합니다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class ReadActiveProductDetailUseCase {

    private final ProductService productService;
    private final BrandService brandService;
    private final LikeService likeService;

    /**
     * @param userId    사용자 ID (비로그인 시 null)
     * @param productId 상품 ID
     * @return 상품 상세 정보 (브랜드, 좋아요 정보 포함)
     */
    @Transactional(readOnly = true)
    public ProductDetail execute(Long userId, Long productId) {
        Product product = productService.getActiveProduct(productId);
        Brand brand = brandService.getActiveBrand(product.getBrandId());
        boolean liked = likeService.isLiked(userId, productId);
        return ProductDetail.from(product, brand, liked);
    }
}
