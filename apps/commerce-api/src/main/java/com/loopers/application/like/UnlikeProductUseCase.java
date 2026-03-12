package com.loopers.application.like;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductWriter;
import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.like.LikeService;

import lombok.RequiredArgsConstructor;

/**
 * 사용자가 상품의 좋아요를 취소합니다.
 *
 * <p>상품 존재 여부를 검증한 뒤 좋아요를 삭제하고, 실제 삭제된 경우 상품의 좋아요 수를 감소시킵니다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class UnlikeProductUseCase {

    private final LikeService likeService;
    private final ProductService productService;
    private final ProductWriter productWriter;

    /**
     * @param userId 사용자 ID
     * @param productId 좋아요 취소할 상품 ID
     */
    @Transactional
    public void execute(Long userId, Long productId) {
        productService.validateActiveProductExists(productId);
        boolean deleted = likeService.unlike(userId, productId);
        if (deleted) {
            productWriter.decreaseLikeCount(productId);
        }
    }
}
