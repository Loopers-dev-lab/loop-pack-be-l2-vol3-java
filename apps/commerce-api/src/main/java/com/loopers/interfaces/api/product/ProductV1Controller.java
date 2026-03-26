package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.page.PagedResult;
import com.loopers.support.enums.ProductSortType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 상품 고객 API V1 REST 엔드포인트를 제공하는 컨트롤러.
 *
 * <p>고객 대상 상품 목록 조회 및 상품 상세 조회 기능을 제공한다.
 * 복잡한 도메인으로 {@link ProductFacade}를 통해 상품 및 브랜드 서비스를 조합하여 처리한다.</p>
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductV1Controller {

    private final ProductFacade productFacade;

    /**
     * 고객용 상품 목록을 조회한다.
     *
     * <p>활성 상태이고 삭제되지 않은 상품만 조회된다.
     * 키워드 또는 브랜드 ID로 필터링, 정렬, 페이징을 지원한다.</p>
     *
     * @param keyword 상품명 검색 키워드 (선택)
     * @param brandId 브랜드 ID 필터 (선택)
     * @param sort    정렬 기준 (LATEST, PRICE_ASC, LIKES_DESC)
     * @param page    페이지 번호 (0부터, 기본값 0)
     * @param size    페이지 크기 (기본값 20)
     * @return 페이징된 상품 목록 응답
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResult<ProductV1Dto.ProductResponse>>> list(
            @RequestParam(value = "q", required = false) String keyword,
            @RequestParam(value = "brandId", required = false) Long brandId,
            @RequestParam(value = "sort", defaultValue = "LATEST") ProductSortType sort,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        PagedResult<ProductInfo> products = productFacade.getProductsForCustomer(keyword, brandId, sort, page, size);
        PagedResult<ProductV1Dto.ProductResponse> response = new PagedResult<>(
                products.content().stream().map(ProductV1Dto.ProductResponse::from).toList(),
                products.page(),
                products.size(),
                products.totalElements(),
                products.totalPages()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 상품 상세 정보를 조회한다.
     *
     * @param productId 조회할 상품 ID
     * @return 상품 상세 정보 응답 (재고 포함)
     */
    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductV1Dto.ProductDetailResponse>> detail(
            @PathVariable Long productId) {
        ProductInfo info = productFacade.getProductDetailForCustomer(productId);
        return ResponseEntity.ok(ApiResponse.success(ProductV1Dto.ProductDetailResponse.from(info)));
    }
}
