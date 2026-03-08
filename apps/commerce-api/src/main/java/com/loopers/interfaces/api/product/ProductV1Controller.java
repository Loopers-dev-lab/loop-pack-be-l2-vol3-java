package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductDto;
import com.loopers.application.product.ProductSortType;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class ProductV1Controller {

    private final ProductFacade productFacade;

    @GetMapping("/api/v1/products")
    public ApiResponse<ProductV1Dto.ProductPageResponse> getProducts(
        @RequestParam(required = false) Long brandId,
        @RequestParam(required = false, defaultValue = "latest") String sort,
        Pageable pageable
    ) {
        try {
            ProductSortType sortType = ProductSortType.from(sort);
            if (brandId != null) {
                return ApiResponse.success(
                    ProductV1Dto.ProductPageResponse.from(
                        productFacade.getProductsByBrand(brandId, sortType, pageable)
                    )
                );
            }
            return ApiResponse.success(ProductV1Dto.ProductPageResponse.from(
                productFacade.getProducts(sortType, pageable)
            ));
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/api/v1/products/{productId}")
    public ApiResponse<ProductV1Dto.ProductResponse> getProduct(@PathVariable Long productId) {
        ProductDto.ProductInfo product = productFacade.getProduct(productId);
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(product));
    }

    @GetMapping("/api-admin/v1/products")
    public ApiResponse<ProductV1Dto.ProductPageResponse> getAdminProducts(
        @RequestParam(required = false) Long brandId,
        @RequestParam(required = false, defaultValue = "latest") String sort,
        Pageable pageable
    ) {
        return getProducts(brandId, sort, pageable);
    }

    @GetMapping("/api-admin/v1/products/{productId}")
    public ApiResponse<ProductV1Dto.ProductResponse> getAdminProduct(@PathVariable Long productId) {
        return getProduct(productId);
    }

    @PostMapping("/api-admin/v1/products")
    public ApiResponse<ProductV1Dto.ProductResponse> createProduct(
        @Valid @RequestBody ProductV1Dto.CreateRequest request
    ) {
        try {
            ProductDto.ProductInfo product = productFacade.register(
                request.brandId(),
                request.name(),
                request.description(),
                request.price(),
                request.stock()
            );
            return ApiResponse.success(ProductV1Dto.ProductResponse.from(product));
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, e.getMessage());
        }
    }

    @PutMapping("/api-admin/v1/products/{productId}")
    public ApiResponse<ProductV1Dto.ProductResponse> updateProduct(
        @PathVariable Long productId,
        @Valid @RequestBody ProductV1Dto.UpdateRequest request
    ) {
        try {
            ProductDto.ProductInfo product = productFacade.update(
                productId,
                request.name(),
                request.description(),
                request.price(),
                request.stock()
            );
            return ApiResponse.success(ProductV1Dto.ProductResponse.from(product));
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/api-admin/v1/products/{productId}")
    public ApiResponse<Void> deleteProduct(@PathVariable Long productId) {
        productFacade.delete(productId);
        return ApiResponse.success(null);
    }

    @PostMapping("/api-admin/v1/products/{productId}/restore")
    public ApiResponse<ProductV1Dto.ProductResponse> restoreProduct(@PathVariable Long productId) {
        ProductDto.ProductInfo product = productFacade.restore(productId);
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(product));
    }
}
