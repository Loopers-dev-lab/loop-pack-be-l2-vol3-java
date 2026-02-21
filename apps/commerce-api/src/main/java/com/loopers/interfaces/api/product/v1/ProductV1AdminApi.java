package com.loopers.interfaces.api.product.v1;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.product.ProductService;
import com.loopers.interfaces.api.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api-admin/v1/products")
public class ProductV1AdminApi implements ProductV1AdminApiSpec {

    private final ProductService productService;

    @PostMapping
    @ResponseStatus(code = HttpStatus.CREATED)
    @Override
    public ApiResponse<ProductDto.CreateProductResponse> createProduct(
            @RequestBody @Valid ProductDto.CreateProductRequest request
    ) {
        Long productId = productService.createProduct(request.toCreateProductCommand());
        return ApiResponse.success(ProductDto.CreateProductResponse.from(productId));
    }
}
