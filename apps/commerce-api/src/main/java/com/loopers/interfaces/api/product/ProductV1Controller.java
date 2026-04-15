package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/products")
@Validated
public class ProductV1Controller implements ProductV1ApiSpec {

    private final ProductFacade productFacade;

    public ProductV1Controller(ProductFacade productFacade) {
        this.productFacade = productFacade;
    }

    /**
     * 상품 목록 조회 API 구현
     * @param brandId 브랜드 ID
     * @param sort 정렬 기준
     * @param page 페이지 (0부터)
     * @param size 페이지 크기
     * @return 상품 목록 조회 결과
     */
    @GetMapping
    @Override
    public ResponseEntity<ApiResponse<ProductV1Dto.ListResponse>> getProductList(
        @RequestParam(required = false) Long brandId,
        @RequestParam(required = false, defaultValue = "latest") String sort,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(0) int size
    ) {
        return ResponseEntity.ok(
            ApiResponse.success(ProductV1Dto.ListResponse.from(productFacade.getProductList(brandId, sort, page, size)))
        );
    }

    /**
     * 신상품 목록 조회 API 구현
     * @param page 페이지 (0부터)
     * @param size 페이지 크기
     * @return 신상품 목록 조회 결과
     */
    @GetMapping("/new-arrivals")
    @Override
    public ResponseEntity<ApiResponse<ProductV1Dto.ListResponse>> getNewArrivals(
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(0) int size
    ) {
        return ResponseEntity.ok(
            ApiResponse.success(ProductV1Dto.ListResponse.from(productFacade.getNewArrivals(page, size)))
        );
    }

    /**
     * 상품 상세 조회 API 구현
     * @param productId 상품 ID
     * @param date 랭킹 기준 일자 yyyyMMdd (생략 시 오늘, Asia/Seoul)
     * @return 상품 상세 조회 결과
     */
    @GetMapping("/{productId}")
    @Override
    public ResponseEntity<ApiResponse<ProductV1Dto.DetailResponse>> getProductDetail(
        @PathVariable Long productId,
        @RequestParam(required = false) String date,
        @RequestParam(required = false) String rankingSnapshotId
    ) {
        Optional<String> snap = Optional.ofNullable(rankingSnapshotId).filter(s -> !s.isBlank());
        return productFacade.getProductDetail(productId, date, snap)
            .map(info -> ResponseEntity.ok(ApiResponse.success(ProductV1Dto.DetailResponse.from(info))))
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: " + productId));
    }
}
