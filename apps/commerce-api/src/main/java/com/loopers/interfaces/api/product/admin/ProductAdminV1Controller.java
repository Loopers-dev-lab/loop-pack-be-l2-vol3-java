package com.loopers.interfaces.api.product.admin;

import com.loopers.application.product.ProductDetail;
import com.loopers.application.product.ProductFacade;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.AdminInfo;
import com.loopers.interfaces.auth.AdminUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/products")
public class ProductAdminV1Controller {

    private final ProductFacade productFacade;
    private final ProductService productService;

    @PostMapping
    public ApiResponse<ProductAdminV1Dto.ProductResponse> create(
        @AdminUser AdminInfo admin,
        @RequestBody ProductAdminV1Dto.CreateRequest request
    ) {
        ProductModel product = productFacade.register(
            request.name(), request.description(), new Money(request.price()),
            request.brandId(), request.initialStock()
        );
        ProductDetail detail = productFacade.getProductForAdmin(product.getId());
        return ApiResponse.success(ProductAdminV1Dto.ProductResponse.from(detail));
    }

    @GetMapping
    public ApiResponse<Page<ProductAdminV1Dto.ProductResponse>> getAll(
        @AdminUser AdminInfo admin,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) Long brandId
    ) {
        Page<ProductDetail> result = productFacade.getProductsForAdmin(brandId, PageRequest.of(page, size));
        return ApiResponse.success(result.map(ProductAdminV1Dto.ProductResponse::from));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductAdminV1Dto.ProductResponse> getProduct(
        @AdminUser AdminInfo admin,
        @PathVariable Long productId
    ) {
        ProductDetail detail = productFacade.getProductForAdmin(productId);
        return ApiResponse.success(ProductAdminV1Dto.ProductResponse.from(detail));
    }

    @PutMapping("/{productId}")
    public ApiResponse<ProductAdminV1Dto.ProductResponse> update(
        @AdminUser AdminInfo admin,
        @PathVariable Long productId,
        @RequestBody ProductAdminV1Dto.UpdateRequest request
    ) {
        productService.update(productId, request.name(), request.description(), new Money(request.price()));
        ProductDetail detail = productFacade.getProductForAdmin(productId);
        return ApiResponse.success(ProductAdminV1Dto.ProductResponse.from(detail));
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Object> delete(@AdminUser AdminInfo admin, @PathVariable Long productId) {
        productService.delete(productId);
        return ApiResponse.success();
    }
}
