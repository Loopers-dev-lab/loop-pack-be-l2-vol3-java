package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductResult;
import com.loopers.application.product.ProductService;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/products")
public class ProductAdminV1Controller implements ProductAdminV1ApiSpec {

    private final ProductService productService;

    @GetMapping
    @Override
    public ApiResponse<Page<ProductV1Dto.ProductResponse>> getAllProducts(
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ApiResponse<ProductV1Dto.ProductResponse> createProduct(
        @Valid @RequestBody ProductV1Dto.ProductCreateRequest request
    ) {
        ProductResult result = productService.create(request.toInfo());
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(result));
    }

    @PutMapping("/{productId}")
    @Override
    public ApiResponse<ProductV1Dto.ProductResponse> updateProduct(
        @PathVariable Long productId,
        @Valid @RequestBody ProductV1Dto.ProductUpdateRequest request
    ) {
        ProductResult result = productService.update(productId, request.toInfo());
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(result));
    }

    @DeleteMapping("/{productId}")
    @Override
    public ApiResponse<Object> deleteProduct(@PathVariable Long productId) {
        productService.delete(productId);
        return ApiResponse.success();
    }
}
