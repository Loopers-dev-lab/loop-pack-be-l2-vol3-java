package com.loopers.interfaces.api.product.v1;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.product.DeleteProductUseCase;
import com.loopers.application.product.ProductResult;
import com.loopers.application.product.ReadProductDetailUseCase;
import com.loopers.application.product.ReadProductsUseCase;
import com.loopers.application.product.RegisterProductUseCase;
import com.loopers.application.product.UpdateProductUseCase;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.support.page.Page;
import com.loopers.support.page.PageSize;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api-admin/v1/products")
public class ProductV1AdminApi implements ProductV1AdminApiSpec {

    private final RegisterProductUseCase registerProductUseCase;
    private final ReadProductsUseCase readProductsUseCase;
    private final ReadProductDetailUseCase readProductDetailUseCase;
    private final UpdateProductUseCase updateProductUseCase;
    private final DeleteProductUseCase deleteProductUseCase;

    @PostMapping
    @ResponseStatus(code = HttpStatus.CREATED)
    @Override
    public ApiResponse<ProductDto.CreateProductResponse> createProduct(
            @RequestBody @Valid ProductDto.CreateProductRequest request
    ) {
        Long productId = registerProductUseCase.execute(request.toCreateProductCommand());
        return ApiResponse.success(ProductDto.CreateProductResponse.from(productId));
    }

    @GetMapping
    @Override
    public ApiResponse<PageResponse<ProductDto.ProductResponse>> getProducts(
            @RequestParam(required = false) Long brandId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<ProductResult> products = readProductsUseCase.execute(brandId, PageSize.withMaxSize(page, size));

        return ApiResponse.success(
                new PageResponse<>(
                        ProductDto.ProductResponse.from(products.content()),
                        products.hasNext()
                )
        );
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductDto.ProductResponse> getProduct(@PathVariable Long productId) {
        ProductResult result = readProductDetailUseCase.execute(productId);
        return ApiResponse.success(ProductDto.ProductResponse.from(result));
    }

    @PutMapping("/{productId}")
    @Override
    public ApiResponse<Object> updateProduct(
            @PathVariable Long productId,
            @RequestBody @Valid ProductDto.UpdateProductRequest request
    ) {
        updateProductUseCase.execute(productId, request.toUpdateProductCommand(productId));
        return ApiResponse.success();
    }

    @DeleteMapping("/{productId}")
    @Override
    public ApiResponse<Object> deleteProduct(@PathVariable Long productId) {
        deleteProductUseCase.execute(productId);
        return ApiResponse.success();
    }
}
