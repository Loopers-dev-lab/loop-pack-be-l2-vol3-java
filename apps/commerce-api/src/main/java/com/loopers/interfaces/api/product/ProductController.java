package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductApplicationService;
import com.loopers.domain.product.Product;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductApplicationService productApplicationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductDto.ProductResponse> createProduct(
            @Valid @RequestBody ProductDto.CreateProductRequest request
    ) {
        Product created = productApplicationService.create(request.toCommand());
        return ApiResponse.success(ProductDto.ProductResponse.from(created));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductDto.ProductResponse> getProduct(@PathVariable Long productId) {
        Product product = productApplicationService.get(productId);
        return ApiResponse.success(ProductDto.ProductResponse.from(product));
    }

    @GetMapping
    public ApiResponse<ProductDto.ProductListResponse> getProducts(ProductListQuery query) {
        Page<Product> products = productApplicationService.list(query.brandId(), query.toPageable());
        return ApiResponse.success(ProductDto.ProductListResponse.from(products));
    }
}
