package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.domain.product.ProductViewedEvent;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductsV1Controller implements ProductsV1ApiSpec {

    private final ProductFacade productFacade;
    private final ApplicationEventPublisher eventPublisher;

    @GetMapping("")
    @Override
    public ApiResponse<ProductV1Dto.PageResponse<ProductV1Dto.ProductListItemResponse>> getProductList(
        @RequestParam(required = false) Long brandId,
        @RequestParam(required = false, defaultValue = "latest") String sort,
        @RequestParam(required = false, defaultValue = "0") int page,
        @RequestParam(required = false, defaultValue = "20") int size
    ) {
        Page<ProductV1Dto.ProductListItemResponse> responsePage = productFacade.getProductList(brandId, sort, page, size)
            .toPage()
            .map(ProductV1Dto.ProductListItemResponse::from);
        return ApiResponse.success(ProductV1Dto.PageResponse.from(responsePage));
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductV1Dto.ProductDetailResponse> getProduct(
        @PathVariable(value = "productId") Long productId,
        @RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
        @RequestHeader(value = "User-Agent", required = false) String userAgent
    ) {
        String userId = (loginId != null) ? loginId : "unknown";
        eventPublisher.publishEvent(new ProductViewedEvent(userId, productId, userAgent));
        return ApiResponse.success(
            ProductV1Dto.ProductDetailResponse.from(productFacade.getProductDetail(productId))
        );
    }

}
