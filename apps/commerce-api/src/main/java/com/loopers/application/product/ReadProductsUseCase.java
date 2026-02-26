package com.loopers.application.product;

import java.util.Objects;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.support.page.Page;
import com.loopers.support.page.PageSize;

import lombok.RequiredArgsConstructor;

/**
 * 어드민이 상품 목록을 조회합니다.
 *
 * <p>삭제된 상품을 포함하며, 브랜드 ID로 필터링할 수 있습니다. 브랜드 ID가 없으면 전체 상품을 조회합니다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class ReadProductsUseCase {

    private final ProductService productService;
    private final BrandService brandService;

    /**
     * @param brandId 브랜드 ID (null이면 전체 조회)
     * @param pageSize 페이지 크기
     * @return 상품 목록 페이지
     */
    @Transactional(readOnly = true)
    public Page<ProductResult> execute(Long brandId, PageSize pageSize) {
        if (Objects.nonNull(brandId)) {
            brandService.validateBrandExists(brandId);
        }
        Page<Product> products = productService.getProducts(brandId, pageSize);
        return products.map(ProductResult::from);
    }
}
