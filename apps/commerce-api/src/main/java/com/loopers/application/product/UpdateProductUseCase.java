package com.loopers.application.product;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.product.ProductService;

import lombok.RequiredArgsConstructor;

/**
 * 어드민이 상품 정보를 수정합니다.
 *
 * <p>상품명, 썸네일, 가격, 재고, 설명을 변경하며, 존재하지 않는 상품인 경우 예외가 발생합니다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class UpdateProductUseCase {

    private final ProductService productService;

    /**
     * @param command 상품 수정 커맨드
     */
    public void execute(ProductCommand.UpdateProductCommand command) {
        productService.update(command.toModifyProduct());
    }
}
