package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api-admin/v1/products")
@RequiredArgsConstructor
public class ProductAdminV1Controller implements ProductAdminApiV1Spec {

    private final ProductFacade productFacade;

    // Command

    @PostMapping
    @Override
    public ApiResponse<ProductAdminV1Dto.ProductResponse> register(
            @Valid @RequestBody ProductAdminV1Dto.RegisterRequest request) {
        ProductInfo info = productFacade.register(
                request.brandId(),
                request.name(),
                request.price(),
                request.stockQuantity(),
                request.description()
        );
        return ApiResponse.success(ProductAdminV1Dto.ProductResponse.from(info));
    }

    @PatchMapping("/{productId}")
    @Override
    public ApiResponse<ProductAdminV1Dto.ProductResponse> update(
            @PathVariable Long productId,
            @Valid @RequestBody ProductAdminV1Dto.UpdateRequest request) {
        ProductInfo info = productFacade.update(
                productId,
                request.name(),
                request.price(),
                request.stockQuantity(),
                request.description()
        );
        return ApiResponse.success(ProductAdminV1Dto.ProductResponse.from(info));
    }
}
