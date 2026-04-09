package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductDetailInfo;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductSortType;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller implements ProductV1ApiSpec {

    private final ProductFacade productFacade;

    // POST /api/v1/products → 상품 등록
    @PostMapping
    @Override
    public ApiResponse<ProductV1Dto.ProductResponse> register(
            @RequestBody ProductV1Dto.RegisterRequest request
    ) {
        ProductInfo info = productFacade.register(
                request.brandId(), request.name(), request.description(),
                request.price(), request.stockQuantity(), request.imageUrl()
        );
        return ApiResponse.success(
                new ProductV1Dto.ProductResponse(
                        info.id(), info.brandId(), info.name(), info.description(), info.imageUrl(),
                        info.price(), info.stockQuantity()
                )
        );
    }

    // GET /api/v1/products/{id} → 상품 상세 조회
    @GetMapping("/{id}")
    @Override
    public ApiResponse<ProductV1Dto.ProductDetailResponse> getById(@PathVariable Long id) {
        ProductDetailInfo info = productFacade.getById(id);
        return ApiResponse.success(toDetailResponse(info));
    }

    // GET /api/v1/products?sortType=LATEST&brandId=1 → 상품 목록 조회
    @GetMapping
    @Override
    public ApiResponse<ProductV1Dto.ProductListResponse> getProducts(
            @RequestParam(defaultValue = "LATEST") String sortType,
            @RequestParam(required = false) Long brandId
    ) {
        ProductSortType sort;
        try {
            sort = ProductSortType.valueOf(sortType);
        } catch (IllegalArgumentException e) {
            String validValues = Arrays.stream(ProductSortType.values())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            throw new CoreException(ErrorType.BAD_REQUEST,
                    String.format("잘못된 정렬 조건입니다. 사용 가능한 값: [%s]", validValues));
        }

        List<ProductDetailInfo> infos;
        if (brandId != null) {
            // 특정 브랜드 상품만 조회
            infos = productFacade.getProductsByBrandId(brandId, sort);
        } else {
            // 전체 상품 조회
            infos = productFacade.getProducts(sort);
        }

        List<ProductV1Dto.ProductDetailResponse> responses = infos.stream()
                .map(this::toDetailResponse)    // 각 Info를 DTO로 변환
                .toList();
        return ApiResponse.success(new ProductV1Dto.ProductListResponse(responses));
    }

    private ProductV1Dto.ProductDetailResponse toDetailResponse(ProductDetailInfo info) {
        return new ProductV1Dto.ProductDetailResponse(
                info.id(), info.name(), info.description(), info.imageUrl(),
                info.price(), info.stockQuantity(), info.brandName(), info.likeCount(),
                info.rank()
        );
    }
}
