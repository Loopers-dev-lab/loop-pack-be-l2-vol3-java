package com.loopers.interfaces.api.admin;

import com.loopers.application.product.ProductApplicationService;
import com.loopers.application.product.ProductAdminFacade;
import com.loopers.application.product.ProductQueryFacade;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.query.ProductListQuery;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.product.ProductDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api-admin/v1/products")
public class AdminProductController {

    private final ProductApplicationService productApplicationService;
    private final ProductAdminFacade productAdminFacade;
    private final ProductQueryFacade productQueryFacade;

    @GetMapping
    public ApiResponse<ProductDto.ProductListResponse> listProducts(ProductListQuery query) {
        return ApiResponse.success(ProductDto.ProductListResponse.from(
                productQueryFacade.listIncludingDeleted(query)
        ));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductDto.ProductResponse> getProduct(@PathVariable Long productId) {
        return ApiResponse.success(ProductDto.ProductResponse.from(productQueryFacade.getIncludingDeleted(productId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductDto.ProductResponse> createProduct(
            @Valid @RequestBody ProductDto.CreateProductRequest request
    ) {
        Product created = productApplicationService.create(request.toCommand());
        return ApiResponse.success(ProductDto.ProductResponse.from(productQueryFacade.toView(created)));
    }

    @PutMapping("/{productId}")
    public ApiResponse<ProductDto.ProductResponse> updateProduct(
            @PathVariable Long productId,
            @Valid @RequestBody ProductDto.UpdateProductRequest request
    ) {
        Product updated = productApplicationService.update(productId, request.toCommand());
        return ApiResponse.success(ProductDto.ProductResponse.from(productQueryFacade.toView(updated)));
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Void> deleteProduct(@PathVariable Long productId) {
        productAdminFacade.delete(productId);
        return ApiResponse.success();
    }
}
