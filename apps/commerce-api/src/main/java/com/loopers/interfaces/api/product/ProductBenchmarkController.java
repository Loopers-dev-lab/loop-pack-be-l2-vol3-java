package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.domain.product.ProductWithBrand;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class ProductBenchmarkController {

    private final ProductFacade productFacade;

    @GetMapping("/no-cache")
    public ApiResponse<ProductDto.PagedProductResponse> getProductsNoCache(
        @RequestParam(required = false) Long brandId,
        @RequestParam(defaultValue = "latest") String sort,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<ProductWithBrand> result;
        if (brandId != null) {
            result = productFacade.getProductsByBrandId(brandId, sort, PageRequest.of(page, size));
        } else {
            result = productFacade.getAllProducts(sort, PageRequest.of(page, size));
        }
        return ApiResponse.success(ProductDto.PagedProductResponse.from(result));
    }

    @GetMapping("/no-optimization")
    public ApiResponse<List<ProductDto.ProductResponse>> getProductsNoOptimization(
        @RequestParam(defaultValue = "latest") String sort
    ) {
        List<ProductWithBrand> products = productFacade.getAllProductsNoOptimization(sort);
        List<ProductDto.ProductResponse> responses = products.stream()
            .map(ProductDto.ProductResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }
}
