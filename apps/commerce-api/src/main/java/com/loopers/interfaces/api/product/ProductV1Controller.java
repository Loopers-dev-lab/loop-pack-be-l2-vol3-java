package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductResult;
import com.loopers.application.product.ProductService;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller implements ProductV1ApiSpec {

    private final ProductService productService;

    @GetMapping
    @Override
    public ApiResponse<Page<ProductV1Dto.ProductResponse>> getProducts(
        @RequestParam(required = false) Long brandId,
        Pageable pageable
    ) {
        Page<ProductResult> results = productService.findAll(brandId, pageable);
        Page<ProductV1Dto.ProductResponse> responses = results.map(ProductV1Dto.ProductResponse::from);
        return ApiResponse.success(responses);
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductV1Dto.ProductResponse> getProduct(@PathVariable Long productId) {
        ProductResult result = productService.findById(productId);
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(result));
    }
}
