package com.loopers.interfaces.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.dto.ProductResult;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.product.dto.AdminProductV1Dto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/products")
public class AdminProductV1Controller implements AdminProductV1ApiSpec {

    private final ProductFacade productFacade;

    @PostMapping
    @Override
    public ApiResponse<Object> register(
        @Valid @RequestBody AdminProductV1Dto.RegisterRequest request
    ) {
        productFacade.registerProduct(request.toCriteria());
        return ApiResponse.success();
    }

    @GetMapping
    @Override
    public ApiResponse<AdminProductV1Dto.ListResponse> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) Long brandId
    ) {
        Page<ProductResult> productInfoPage = brandId != null
            ? productFacade.getProductsByBrandId(brandId, PageRequest.of(page, size))
            : productFacade.getProducts(PageRequest.of(page, size));

        return ApiResponse.success(
                new AdminProductV1Dto.ListResponse(
                        productInfoPage.getNumber(),
                        productInfoPage.getSize(),
                        productInfoPage.getTotalElements(),
                        productInfoPage.getTotalPages(),
                        productInfoPage.getContent().stream()
                                .map(AdminProductV1Dto.ListResponse.ListItem::from)
                                .toList()));
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<AdminProductV1Dto.DetailResponse> getById(
        @PathVariable Long productId
    ) {
        ProductResult productInfo = productFacade.getProduct(productId);
        return ApiResponse.success(AdminProductV1Dto.DetailResponse.from(productInfo));
    }

    @PutMapping("/{productId}")
    @Override
    public ApiResponse<Object> update(
        @PathVariable Long productId,
        @Valid @RequestBody AdminProductV1Dto.UpdateRequest request
    ) {
        productFacade.updateProduct(productId, request.toCriteria());
        return ApiResponse.success();
    }

    @DeleteMapping("/{productId}")
    @Override
    public ApiResponse<Object> delete(
        @PathVariable Long productId
    ) {
        productFacade.deleteProduct(productId);
        return ApiResponse.success();
    }
}
