package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.ProductSortType;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AdminUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/products")
public class ProductAdminV1Controller implements ProductAdminV1ApiSpec {

    private final ProductFacade productFacade;

    @PostMapping
    @Override
    public ApiResponse<ProductAdminV1Dto.ProductAdminDetailResponse> create(
        @AdminUser String adminLdap,
        @RequestBody ProductAdminV1Dto.CreateRequest request
    ) {
        ProductInfo.AdminDetail info = productFacade.register(
            request.name(), request.description(), new Money(request.price()),
            request.brandId(), request.stockQuantity()
        );
        return ApiResponse.success(ProductAdminV1Dto.ProductAdminDetailResponse.from(info));
    }

    @GetMapping
    @Override
    public ApiResponse<Page<ProductAdminV1Dto.ProductAdminSummaryResponse>> getAll(
        @AdminUser String adminLdap,
        Pageable pageable,
        @RequestParam(value = "sortType", defaultValue = "CREATED_DESC") String sortType
    ) {
        ProductSortType sort = ProductSortType.valueOf(sortType);
        Page<ProductAdminV1Dto.ProductAdminSummaryResponse> response = productFacade.getAllForAdmin(pageable, sort)
            .map(ProductAdminV1Dto.ProductAdminSummaryResponse::from);
        return ApiResponse.success(response);
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductAdminV1Dto.ProductAdminDetailResponse> getById(
        @AdminUser String adminLdap,
        @PathVariable(value = "productId") Long productId
    ) {
        ProductInfo.AdminDetail info = productFacade.getDetailForAdmin(productId);
        return ApiResponse.success(ProductAdminV1Dto.ProductAdminDetailResponse.from(info));
    }

    @PutMapping("/{productId}")
    @Override
    public ApiResponse<ProductAdminV1Dto.ProductAdminDetailResponse> update(
        @AdminUser String adminLdap,
        @PathVariable(value = "productId") Long productId,
        @RequestBody ProductAdminV1Dto.UpdateRequest request
    ) {
        ProductInfo.AdminDetail info = productFacade.update(
            productId, request.name(), request.description(), new Money(request.price())
        );
        return ApiResponse.success(ProductAdminV1Dto.ProductAdminDetailResponse.from(info));
    }

    @DeleteMapping("/{productId}")
    @Override
    public ApiResponse<Object> delete(
        @AdminUser String adminLdap,
        @PathVariable(value = "productId") Long productId
    ) {
        productFacade.delete(productId);
        return ApiResponse.success();
    }

    @PatchMapping("/{productId}/stock")
    @Override
    public ApiResponse<ProductAdminV1Dto.ProductAdminDetailResponse> updateStock(
        @AdminUser String adminLdap,
        @PathVariable(value = "productId") Long productId,
        @RequestBody ProductAdminV1Dto.UpdateStockRequest request
    ) {
        ProductInfo.AdminDetail info = productFacade.updateStock(productId, request.quantity());
        return ApiResponse.success(ProductAdminV1Dto.ProductAdminDetailResponse.from(info));
    }
}
