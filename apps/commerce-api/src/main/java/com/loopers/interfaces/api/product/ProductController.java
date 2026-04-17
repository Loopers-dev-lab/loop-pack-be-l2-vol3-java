package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductFacade productFacade;

    @GetMapping
    public ApiResponse<ProductDto.PagedProductResponse> getProducts(
        @RequestParam(required = false) Long brandId,
        @RequestParam(defaultValue = "latest") String sort,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        ProductDto.PagedProductResponse response = productFacade.getAllProductsCached(brandId, sort, page, size);
        return ApiResponse.success(response);
    }

    @GetMapping("/new")
    public ApiResponse<ProductDto.PagedProductResponse> getNewProducts(
        @RequestParam(defaultValue = "48") @Min(1) @Max(168) int hours,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(productFacade.getNewProducts(hours, page, size));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductDto.ProductResponse> getProduct(@PathVariable Long productId) {
        ProductDto.ProductResponse response = productFacade.getProductDetailCached(productId);
        return ApiResponse.success(response);
    }
}
