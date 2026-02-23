package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductCreateCommand;
import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductService;
import com.loopers.application.product.ProductUpdateCommand;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.product.dto.ProductV1Dto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/products")
public class AdminProductV1Controller {

    private final ProductService productService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductV1Dto.AdminProductResponse> createProduct(
            @RequestHeader("X-Loopers-Ldap") String ldap,
            @Valid @RequestBody ProductV1Dto.CreateRequest request
    ) {
        ProductInfo product = productService.register(
                new ProductCreateCommand(request.brandId(), request.name(), request.description(),
                        request.price(), request.stockQuantity())
        );

        return ApiResponse.success(ProductV1Dto.AdminProductResponse.from(product));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductV1Dto.AdminProductResponse> getProduct(
            @RequestHeader("X-Loopers-Ldap") String ldap,
            @PathVariable Long productId
    ) {
        ProductInfo product = productService.getProduct(productId);
        return ApiResponse.success(ProductV1Dto.AdminProductResponse.from(product));
    }

    @GetMapping
    public ApiResponse<PageResponse<ProductV1Dto.AdminProductResponse>> getProducts(
            @RequestHeader("X-Loopers-Ldap") String ldap,
            @RequestParam(required = false) Long brandId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<ProductV1Dto.AdminProductResponse> page = productService.getAdminProducts(brandId, pageable)
                .map(ProductV1Dto.AdminProductResponse::from);
        return ApiResponse.success(PageResponse.from(page));
    }

    @PutMapping("/{productId}")
    public ApiResponse<ProductV1Dto.AdminProductResponse> updateProduct(
            @RequestHeader("X-Loopers-Ldap") String ldap,
            @PathVariable Long productId,
            @Valid @RequestBody ProductV1Dto.UpdateRequest request
    ) {
        ProductInfo product = productService.update(
                productId, new ProductUpdateCommand(request.name(), request.description(),
                        request.price(), request.stockQuantity(), request.visibility())
        );
        return ApiResponse.success(ProductV1Dto.AdminProductResponse.from(product));
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Void> deleteProduct(
            @RequestHeader("X-Loopers-Ldap") String ldap,
            @PathVariable Long productId
    ) {
        productService.delete(productId);
        return ApiResponse.success(null);
    }
}
