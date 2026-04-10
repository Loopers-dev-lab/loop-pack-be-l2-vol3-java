package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductWithBrand;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api-admin/v1/products")
public class ProductAdminController {

    private final ProductFacade productFacade;

    @GetMapping
    public ApiResponse<List<ProductDto.ProductResponse>> getAllProducts() {
        List<ProductDto.ProductResponse> responses = productFacade.getAllProducts().stream()
            .map(ProductDto.ProductResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductDto.ProductResponse> getProduct(@PathVariable Long productId) {
        ProductWithBrand info = productFacade.getProductDetail(productId);
        return ApiResponse.success(ProductDto.ProductResponse.from(info));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductDto.ProductResponse> createProduct(@Valid @RequestBody ProductDto.CreateRequest request) {
        Product product = productFacade.createProduct(
            request.brandId(), request.name(), request.price(), request.stockQuantity(), request.categoryId());
        return ApiResponse.success(ProductDto.ProductResponse.from(product));
    }

    @PutMapping("/{productId}")
    public ApiResponse<ProductDto.ProductResponse> updateProduct(
        @PathVariable Long productId,
        @Valid @RequestBody ProductDto.UpdateRequest request
    ) {
        Product product = productFacade.updateProduct(
            productId, request.name(), request.price(), request.stockQuantity());
        return ApiResponse.success(ProductDto.ProductResponse.from(product));
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Object> deleteProduct(@PathVariable Long productId) {
        productFacade.deleteProduct(productId);
        return ApiResponse.success();
    }
}
