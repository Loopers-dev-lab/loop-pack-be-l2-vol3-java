package com.loopers.application.product;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductWriter;

import lombok.RequiredArgsConstructor;

/**
 * 어드민이 상품을 삭제합니다.
 *
 * <p>상품을 소프트 삭제하고, 해당 상품의 모든 좋아요를 함께 삭제합니다.
 * 이미 삭제된 상품인 경우 아무 작업도 수행하지 않습니다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class DeleteProductUseCase {

    private final ProductWriter productWriter;
    private final LikeService likeService;

    /**
     * @param productId 삭제할 상품 ID
     */
    @Transactional
    public void execute(Long productId) {
        boolean deleted = productWriter.delete(productId);
        if (!deleted) {
            return;
        }
        likeService.deleteLikesByProductId(productId);
    }
}
