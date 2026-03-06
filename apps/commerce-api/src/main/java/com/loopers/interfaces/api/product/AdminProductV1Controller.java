package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductApplicationService;
import com.loopers.application.product.ProductPageWithBrandsAndStocks;
import com.loopers.application.product.ProductWithBrandAndStock;
import com.loopers.application.product.RegisterProductCommand;
import com.loopers.application.product.UpdateProductCommand;
import com.loopers.domain.product.ProductSortType;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
public class AdminProductV1Controller implements AdminProductV1ApiSpec {

    private final ProductApplicationService productApplicationService;

    @PostMapping
    @Override
    public ApiResponse<AdminProductV1Dto.ProductResponse> create(@Valid @RequestBody AdminProductV1Dto.CreateRequest request) {
        RegisterProductCommand command = new RegisterProductCommand(
            request.brandId(), request.name(), request.price(), request.stock());
        ProductWithBrandAndStock result = productApplicationService.registerWithStock(command);
        return ApiResponse.success(AdminProductV1Dto.ProductResponse.from(result.product(), result.brand(), result.productStock()));
    }

    @GetMapping
    @Override
    public ApiResponse<AdminProductV1Dto.ProductPageResponse> getAll(
        @RequestParam(required = false) Long brandId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        ProductPageWithBrandsAndStocks result = productApplicationService.getAllForAdmin(brandId, ProductSortType.LATEST, page, size);
        return ApiResponse.success(AdminProductV1Dto.ProductPageResponse.from(result.result(), result.brandMap(), result.stockMap()));
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<AdminProductV1Dto.ProductResponse> getById(@PathVariable Long productId) {
        ProductWithBrandAndStock result = productApplicationService.getProductWithBrandAndStock(productId);
        return ApiResponse.success(AdminProductV1Dto.ProductResponse.from(result.product(), result.brand(), result.productStock()));
    }

    @PutMapping("/{productId}")
    @Override
    public ApiResponse<AdminProductV1Dto.ProductResponse> update(
        @PathVariable Long productId,
        @Valid @RequestBody AdminProductV1Dto.UpdateRequest request
    ) {
        UpdateProductCommand command = new UpdateProductCommand(
            productId, request.name(), request.price(), request.stock());
        ProductWithBrandAndStock result = productApplicationService.updateWithStock(command);
        return ApiResponse.success(AdminProductV1Dto.ProductResponse.from(result.product(), result.brand(), result.productStock()));
    }

    @DeleteMapping("/{productId}")
    @Override
    public ApiResponse<Void> delete(@PathVariable Long productId) {
        productApplicationService.delete(productId);
        return ApiResponse.success();
    }
}
