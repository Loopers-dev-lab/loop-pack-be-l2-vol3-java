package com.loopers.application.product;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;

import lombok.RequiredArgsConstructor;

/**
 * 어드민이 새로운 상품을 등록합니다.
 *
 * <p>브랜드 존재 여부를 검증한 뒤 상품을 생성하고, 생성된 상품 ID를 반환합니다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class RegisterProductUseCase {

    private final BrandService brandService;
    private final ProductService productService;

    /**
     * @param command 상품 생성 커맨드
     * @return 생성된 상품 ID
     */
    @Transactional
    public Long execute(ProductCommand.CreateProductCommand command) {
        brandService.validateActiveBrandExists(command.brandId());
        Product product = productService.create(
                command.brandId(),
                command.name(),
                command.thumbnailUrl(),
                command.price(),
                command.stock(),
                command.description()
        );
        return product.getId();
    }
}
