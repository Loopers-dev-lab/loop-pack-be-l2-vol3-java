package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller implements ProductV1ApiSpec {

    private final ProductFacade productFacade;

    @GetMapping
    @Override
    public ApiResponse<ProductV1Dto.ProductListResponse> getProducts(
            @RequestParam(required = false) Long brandId,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size)
    {
        var pageable = PageRequest.of(page, size, ProductSortType.from(sort).toSort());
        var result = productFacade.findAll(brandId, pageable);
        return ApiResponse.success(ProductV1Dto.ProductListResponse.from(result));
    }

    @GetMapping("/{id}")
    @Override
    public ApiResponse<ProductV1Dto.ProductResponse> getProductDetails(
            @PathVariable long id)
    {
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(productFacade.findById(id)));
    }
}
