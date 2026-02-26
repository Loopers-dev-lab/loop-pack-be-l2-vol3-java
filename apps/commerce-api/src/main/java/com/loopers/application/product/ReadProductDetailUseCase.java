package com.loopers.application.product;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

/**
 * 어드민이 상품 상세 정보를 조회합니다.
 *
 * <p>삭제된 상품도 조회 가능하며, 존재하지 않는 상품인 경우 예외가 발생합니다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class ReadProductDetailUseCase {

    private final ProductRepository productRepository;

    /**
     * @param productId 상품 ID
     * @return 상품 상세 정보
     */
    public ProductResult execute(Long productId) {
        return productRepository.findById(productId)
                .map(ProductResult::from)
                .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
    }
}
