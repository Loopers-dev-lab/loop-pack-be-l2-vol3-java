package com.loopers.application.product;

import java.util.List;
import java.util.Objects;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortType;
import com.loopers.support.page.Page;
import com.loopers.support.page.PageSize;

import lombok.RequiredArgsConstructor;

/**
 * 사용자가 활성 상품 목록을 조회합니다.
 *
 * <p>브랜드 정보와 좋아요 여부를 조합하여 반환하며, 브랜드 ID로 필터링하고 정렬 기준을 지정할 수 있습니다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class ReadActiveProductsUseCase {

    private final ProductService productService;
    private final BrandService brandService;
    private final ProductDetailAssembler productDetailAssembler;

    /**
     * @param userId   사용자 ID (비로그인 시 null)
     * @param brandId  브랜드 ID (null이면 전체 조회)
     * @param sortType 정렬 기준
     * @param pageSize 페이지 크기
     * @return 상품 상세 목록 페이지 (브랜드, 좋아요 정보 포함)
     */
    @Transactional(readOnly = true)
    public Page<ProductDetail> execute(Long userId, Long brandId, ProductSortType sortType, PageSize pageSize) {
        if (Objects.nonNull(brandId)) {
            brandService.validateActiveBrandExists(brandId);
        }
        Page<Product> products = productService.getActiveProducts(brandId, sortType, pageSize);
        List<ProductDetail> results = productDetailAssembler.assemble(products.content(), userId);
        return new Page<>(results, products.hasNext());
    }
}
