package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/{productId}")
    public ApiResponse<ProductDto.ProductResponse> getProduct(@PathVariable Long productId) {
        ProductDto.ProductResponse response = productFacade.getProductDetailCached(productId);
        return ApiResponse.success(response);
    }
}
