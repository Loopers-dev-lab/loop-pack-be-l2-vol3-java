package com.loopers.interfaces.api.product.v1;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.product.ProductDetail;
import com.loopers.application.product.ProductService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.LoginUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class ProductV1Api implements ProductV1ApiSpec {

    private final ProductService productService;

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductDto.ProductDetailResponse> getActiveProduct(
            @LoginUser Long userId,
            @PathVariable Long productId
    ) {
        ProductDetail product = productService.getActiveProduct(userId, productId);
        return ApiResponse.success(ProductDto.ProductDetailResponse.from(product));
    }
}
