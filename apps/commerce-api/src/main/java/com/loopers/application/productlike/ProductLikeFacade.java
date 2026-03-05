package com.loopers.application.productlike;

import com.loopers.domain.product.ProductService;
import com.loopers.domain.productlike.ProductLike;
import com.loopers.domain.productlike.ProductLikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductLikeFacade {

    private final ProductLikeService productLikeService;
    private final ProductService productService;

    @Transactional
    public ProductLikeInfo registerLike(Long userId, Long productId) {
        // 상품 좋아요 수 증가 (비관적 락 획득으로 동시성 제어 + 상품 존재 확인)
        productService.increaseLikes(productId);

        // 좋아요 등록 (직렬화되어 중복 체크 안전)
        ProductLike productLike = productLikeService.registerLike(userId, productId);

        return ProductLikeInfo.from(productLike);
    }

    @Transactional
    public void cancelLike(Long userId, Long productId) {
        // 상품 좋아요 수 감소 (비관적 락 획득으로 동시성 제어 + 상품 존재 확인)
        productService.decreaseLikes(productId);

        // 좋아요 취소
        productLikeService.cancelLike(userId, productId);
    }

    public Page<ProductLikeInfo> getLikesByUserId(Long userId, Pageable pageable) {
        return productLikeService.getLikesByUserId(userId, pageable)
                .map(ProductLikeInfo::from);
    }
}
