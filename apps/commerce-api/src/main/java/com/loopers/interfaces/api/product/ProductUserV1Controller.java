package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductRequest;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductUserV1Controller implements ProductUserApiV1Spec {

    private final ProductFacade productFacade;

    // Query

    @GetMapping
    @Override
    public ApiResponse<PageResponse<ProductUserV1Dto.ProductResponse>> list(
            ProductRequest.ListActive request) {
        Page<ProductInfo> products = productFacade.getActiveList(request);
        PageResponse<ProductUserV1Dto.ProductResponse> pageResponse =
                PageResponse.from(products, ProductUserV1Dto.ProductResponse::from);
        return ApiResponse.success(pageResponse);
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductUserV1Dto.ProductResponse> detail(@PathVariable Long productId) {
        ProductInfo info = productFacade.getActiveDetail(productId);
        return ApiResponse.success(ProductUserV1Dto.ProductResponse.from(info));
    }
}
