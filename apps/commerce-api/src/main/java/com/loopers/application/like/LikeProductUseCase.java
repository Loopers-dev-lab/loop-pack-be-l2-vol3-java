package com.loopers.application.like;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductService;

import lombok.RequiredArgsConstructor;

/**
 * 사용자가 상품에 좋아요를 추가합니다.
 *
 * <p>상품 존재 여부를 검증한 뒤 좋아요를 등록한다.
 * 좋아요 수 갱신은 {@link com.loopers.domain.like.LikeEvent.Liked} 이벤트를 통해 비동기로 처리된다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class LikeProductUseCase {

    private final LikeService likeService;
    private final ProductService productService;

    /**
     * @param userId 사용자 ID
     * @param productId 좋아요할 상품 ID
     */
    @Transactional
    public void execute(Long userId, Long productId) {
        productService.validateActiveProductExists(productId);
        likeService.like(userId, productId);
    }
}
