package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductApplicationService;
import com.loopers.application.product.PublicProductListQueryApplicationService;
import com.loopers.application.product.ProductQueryFacade;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.query.ProductListCriteria;
import com.loopers.domain.product.query.ProductListQuery;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductApplicationService productApplicationService;
    private final ProductQueryFacade productQueryFacade;
    private final PublicProductListQueryApplicationService publicProductListQueryApplicationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductDto.ProductResponse> createProduct(
            @Valid @RequestBody ProductDto.CreateProductRequest request
    ) {
        Product created = productApplicationService.create(request.toCommand());
        return ApiResponse.success(ProductDto.ProductResponse.from(productQueryFacade.toView(created)));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductDto.ProductResponse> getProduct(@PathVariable UUID productId) {
        return ApiResponse.success(ProductDto.ProductResponse.from(productQueryFacade.get(productId)));
    }

    @GetMapping
    public ApiResponse<ProductDto.PublicProductListResponse> getProducts(ProductListQuery query) {
        return ApiResponse.success(ProductDto.PublicProductListResponse.from(
                publicProductListQueryApplicationService.list(ProductListCriteria.fromPublic(query))
        ));
    }
}
