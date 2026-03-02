package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.product.ProductSortType;
import com.loopers.domain.product.ProductStatus;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/products")
public class ProductAdminV1Controller implements ProductAdminV1ApiSpec {

    private final ProductFacade productFacade;

    @GetMapping
    @Override
    public ApiResponse<Page<ProductAdminV1Dto.ProductResponse>> getAll(Pageable pageable) {
        Page<ProductAdminV1Dto.ProductResponse> response = productFacade.getAll(pageable, ProductSortType.LATEST)
            .map(ProductAdminV1Dto.ProductResponse::from);
        return ApiResponse.success(response);
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductAdminV1Dto.ProductResponse> getProduct(@PathVariable Long productId) {
        ProductInfo info = productFacade.getProduct(productId);
        return ApiResponse.success(ProductAdminV1Dto.ProductResponse.from(info));
    }

    @PostMapping
    @Override
    public ApiResponse<ProductAdminV1Dto.ProductResponse> register(
        @Valid @RequestBody ProductAdminV1Dto.RegisterRequest request
    ) {
        ProductInfo info = productFacade.register(
            request.brandId(),
            request.name(),
            request.price(),
            request.description(),
            request.stockQuantity(),
            ProductStatus.valueOf(request.status())
        );
        return ApiResponse.success(ProductAdminV1Dto.ProductResponse.from(info));
    }

    @PutMapping("/{productId}")
    @Override
    public ApiResponse<ProductAdminV1Dto.ProductResponse> update(
        @PathVariable Long productId,
        @Valid @RequestBody ProductAdminV1Dto.UpdateRequest request
    ) {
        ProductInfo info = productFacade.update(
            productId,
            request.brandId(),
            request.name(),
            request.price(),
            request.description(),
            request.stockQuantity(),
            ProductStatus.valueOf(request.status())
        );
        return ApiResponse.success(ProductAdminV1Dto.ProductResponse.from(info));
    }

    @DeleteMapping("/{productId}")
    @Override
    public ApiResponse<Void> delete(@PathVariable Long productId) {
        productFacade.delete(productId);
        return ApiResponse.success(null);
    }
}
