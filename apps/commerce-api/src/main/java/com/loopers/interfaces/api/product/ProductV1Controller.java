package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductDetailInfo;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductListInfo;
import com.loopers.domain.product.SortCondition;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller implements ProductV1ApiSpec {

    private final ProductFacade productFacade;

    @GetMapping
    @Override
    public ApiResponse<List<ProductV1Dto.ProductListResponse>> getProductList(
        @RequestParam(defaultValue = "latest") SortCondition sort
    ) {
        List<ProductListInfo> productList = productFacade.getProductList(sort);
        List<ProductV1Dto.ProductListResponse> response = productList.stream()
            .map(ProductV1Dto.ProductListResponse::from)
            .toList();
        return ApiResponse.success(response);
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductV1Dto.ProductDetailResponse> getProductDetail(
        @PathVariable Long productId
    ) {
        ProductDetailInfo info = productFacade.getProductDetail(productId);
        productFacade.publishViewedEvent(productId);
        return ApiResponse.success(ProductV1Dto.ProductDetailResponse.from(info));
    }
}
