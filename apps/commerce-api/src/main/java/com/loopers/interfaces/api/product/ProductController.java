package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.domain.product.ProductWithBrand;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductFacade productFacade;

    @GetMapping
    public ApiResponse<List<ProductDto.ProductResponse>> getProducts(
        @RequestParam(required = false) Long brandId,
        @RequestParam(defaultValue = "latest") String sort
    ) {
        List<ProductWithBrand> products;
        if (brandId != null) {
            products = productFacade.getProductsByBrandId(brandId);
        } else {
            products = productFacade.getAllProducts(sort);
        }
        List<ProductDto.ProductResponse> responses = products.stream()
            .map(ProductDto.ProductResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductDto.ProductResponse> getProduct(@PathVariable Long productId) {
        ProductWithBrand info = productFacade.getProductDetail(productId);
        return ApiResponse.success(ProductDto.ProductResponse.from(info));
    }
}
