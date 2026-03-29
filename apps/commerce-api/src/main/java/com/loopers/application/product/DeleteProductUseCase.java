package com.loopers.application.product;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.application.product.cache.ProductCacheWriter;
import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.product.ProductEvent.ProductDeleted;

import lombok.RequiredArgsConstructor;

/**
 * 어드민이 상품을 삭제합니다.
 *
 * <p>상품을 소프트 삭제하고 캐시를 무효화한다.
 * 좋아요 삭제는 {@link ProductDeleted} 이벤트를 통해 비동기로 처리된다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class DeleteProductUseCase {

    private final ProductCacheWriter productCacheWriter;

    /**
     * @param productId 삭제할 상품 ID
     */
    @Transactional
    public void execute(Long productId) {
        productCacheWriter.delete(productId);
    }
}
