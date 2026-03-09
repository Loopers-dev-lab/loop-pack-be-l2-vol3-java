package com.loopers.interfaces.api.admin;

import com.loopers.application.product.ProductFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api-admin/v1/products")
public class AdminProductV1Controller implements AdminProductV1ApiSpec {

    private final ProductFacade productFacade;

    public AdminProductV1Controller(ProductFacade productFacade) {
        this.productFacade = productFacade;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> createProduct(
        @Valid @RequestBody AdminProductV1Dto.CreateProductRequest request
    ) {
        var info = productFacade.registerProduct(
            request.brandId(),
            request.name(),
            request.price(),
            request.stockQuantity() != null ? request.stockQuantity() : 0
        );
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(toResponse(info)));
    }

    @GetMapping("/{productId}")
    @Override
    public ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> getProduct(
        @PathVariable Long productId
    ) {
        var info = productFacade.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: " + productId));
        return ResponseEntity.ok(ApiResponse.success(toResponse(info)));
    }

    @PutMapping("/{productId}")
    @Override
    public ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> updateProduct(
        @PathVariable Long productId,
        @Valid @RequestBody AdminProductV1Dto.UpdateProductRequest request
    ) {
        var info = productFacade.updateProduct(
            productId,
            request.name(),
            request.price(),
            request.stockQuantity() != null ? request.stockQuantity() : 0
        );
        return ResponseEntity.ok(ApiResponse.success(toResponse(info)));
    }

    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Override
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
        @PathVariable Long productId
    ) {
        productFacade.deleteProduct(productId);
        return ResponseEntity.noContent().build();
    }

    private static AdminProductV1Dto.ProductResponse toResponse(com.loopers.application.product.ProductInfo info) {
        return new AdminProductV1Dto.ProductResponse(
            info.id(),
            info.brandId(),
            info.name(),
            info.price(),
            info.stockQuantity(),
            info.deleted()
        );
    }
}
