package com.loopers.application.like;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductService;

import lombok.RequiredArgsConstructor;

/**
 * 사용자가 상품의 좋아요를 취소합니다.
 *
 * <p>상품 존재 여부를 검증한 뒤 좋아요를 삭제한다.
 * 좋아요 수 갱신은 {@link com.loopers.domain.like.LikeEvent.Unliked} 이벤트를 통해 비동기로 처리된다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class UnlikeProductUseCase {

    private final LikeService likeService;
    private final ProductService productService;

    /**
     * @param userId 사용자 ID
     * @param productId 좋아요 취소할 상품 ID
     */
    @Transactional
    public void execute(Long userId, Long productId) {
        productService.validateActiveProductExists(productId);
        likeService.unlike(userId, productId);
    }
}
