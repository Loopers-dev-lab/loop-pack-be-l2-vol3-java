package com.loopers.interfaces.api.product.v1;

import java.util.Objects;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.product.ProductService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.support.page.PageSize;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api-admin/v1/products")
public class ProductV1AdminApi implements ProductV1AdminApiSpec {

    private final ProductService productService;

    @PostMapping
    @ResponseStatus(code = HttpStatus.CREATED)
    @Override
    public ApiResponse<ProductDto.CreateProductResponse> createProduct(
            @RequestBody @Valid ProductDto.CreateProductRequest request
    ) {
        Long productId = productService.createProduct(request.toCreateProductCommand());
        return ApiResponse.success(ProductDto.CreateProductResponse.from(productId));
    }

    @GetMapping
    @Override
    public ApiResponse<PageResponse<ProductDto.ProductResponse>> getProducts(
            @RequestParam(required = false) Long brandId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var pageSize = new PageSize(page, size);
        var products = Objects.isNull(brandId)
                ? productService.getProducts(pageSize)
                : productService.getProductsByBrandId(brandId, pageSize);

        return ApiResponse.success(
                new PageResponse<>(
                        ProductDto.ProductResponse.from(products.content()),
                        products.hasNext()
                )
        );
    }
}
