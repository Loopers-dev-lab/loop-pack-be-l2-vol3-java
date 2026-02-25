package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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

    @DeleteMapping("/{productId}")
    @Override
    public ApiResponse<Void> delete(@PathVariable Long productId) {
        productFacade.delete(productId);
        return ApiResponse.success();
    }

    // Query

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductAdminV1Dto.ProductResponse> detail(@PathVariable Long productId) {
        ProductInfo info = productFacade.getDetail(productId);
        return ApiResponse.success(ProductAdminV1Dto.ProductResponse.from(info));
    }

    @GetMapping
    @Override
    public ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>> list(
            @Valid ProductAdminV1Dto.ListRequest request) {
        Page<ProductInfo> products = productFacade.getList(
                request.name(), request.brandId(), request.toDeleted(), request.toPageable());
        PageResponse<ProductAdminV1Dto.ProductResponse> pageResponse =
                PageResponse.from(products, ProductAdminV1Dto.ProductResponse::from);
        return ApiResponse.success(pageResponse);
    }
}
