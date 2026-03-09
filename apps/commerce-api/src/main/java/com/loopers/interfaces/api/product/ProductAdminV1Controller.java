package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.product.RegisterProductCommand;
import com.loopers.domain.product.UpdateProductCommand;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api-admin/v1/products")
@RequiredArgsConstructor
public class ProductAdminV1Controller {

    private final ProductFacade productFacade;

    @PostMapping
    public ApiResponse<ProductV1Dto.Response> registerProduct(@RequestBody ProductV1Dto.RegisterRequest request) {
        RegisterProductCommand command = new RegisterProductCommand(
                request.brandId(), request.name(), request.description(),
                request.price(), request.stock(), request.imageUrl()
        );
        ProductInfo productInfo = productFacade.registerProduct(command);
        return ApiResponse.success(ProductV1Dto.Response.from(productInfo));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductV1Dto.Response> getProduct(@PathVariable Long productId) {
        ProductInfo productInfo = productFacade.getProduct(productId);
        return ApiResponse.success(ProductV1Dto.Response.from(productInfo));
    }

    @GetMapping
    public ApiResponse<ProductV1Dto.PageResponse> getProducts(
            @RequestParam(required = false) Long brandId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<ProductInfo> productInfos = brandId != null
                ? productFacade.getProductsByBrandId(brandId, pageable)
                : productFacade.getProducts(pageable);

        return ApiResponse.success(ProductV1Dto.PageResponse.from(productInfos));
    }

    @PutMapping("/{productId}")
    public ApiResponse<ProductV1Dto.Response> updateProduct(
            @PathVariable Long productId,
            @RequestBody ProductV1Dto.UpdateRequest request
    ) {
        UpdateProductCommand command = new UpdateProductCommand(
                request.name(), request.description(),
                request.price(), request.stock(), request.imageUrl()
        );
        ProductInfo productInfo = productFacade.updateProduct(productId, command);
        return ApiResponse.success(ProductV1Dto.Response.from(productInfo));
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Void> deleteProduct(@PathVariable Long productId) {
        productFacade.deleteProduct(productId);
        return ApiResponse.success(null);
    }
}
