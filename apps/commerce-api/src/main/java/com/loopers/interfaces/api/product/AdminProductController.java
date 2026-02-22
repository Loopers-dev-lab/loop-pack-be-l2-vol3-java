package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductAdminFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthAdmin;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 상품 관리 어드민 API 컨트롤러 (X-Loopers-Ldap 인증 필요) */
@RestController
@RequestMapping("/api-admin/v1/products")
public class AdminProductController implements AdminProductApiSpec {

    private final ProductAdminFacade productAdminFacade;
    private final ProductService productService;

    public AdminProductController(ProductAdminFacade productAdminFacade, ProductService productService) {
        this.productAdminFacade = productAdminFacade;
        this.productService = productService;
    }

    /** 전체 상품 목록 페이지네이션 조회 */
    @GetMapping
    @Override
    public ApiResponse<AdminProductResponse.ProductListResponse> getProducts(
            @AuthAdmin String ldap,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long brandId
    ) {
        ProductAdminFacade.ProductAdminListResult result = productAdminFacade.getProducts(page, size, brandId);

        List<AdminProductResponse.ProductDetail> details = result.products().stream()
                .map(AdminProductResponse.ProductDetail::from)
                .toList();

        return ApiResponse.success(new AdminProductResponse.ProductListResponse(
                details, result.page(), result.size(), result.totalElements(), result.totalPages()));
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<AdminProductResponse.ProductDetail> getProduct(
            @AuthAdmin String ldap,
            @PathVariable Long productId
    ) {
        ProductAdminFacade.ProductAdminDetailResult result = productAdminFacade.getProductDetail(productId);
        return ApiResponse.success(AdminProductResponse.ProductDetail.from(
                result.product(), result.brand(), result.inventory()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ApiResponse<AdminProductResponse.ProductDetail> createProduct(
            @AuthAdmin String ldap,
            @RequestBody AdminProductRequest.CreateProductRequest request
    ) {
        ProductAdminFacade.ProductAdminDetailResult result = productAdminFacade.createProduct(
                request.brandId(), request.name(), request.description(),
                request.basePrice(), request.quantity());
        return ApiResponse.success(AdminProductResponse.ProductDetail.from(
                result.product(), result.brand(), result.inventory()));
    }

    @PutMapping("/{productId}")
    @Override
    public ApiResponse<AdminProductResponse.ProductDetail> updateProduct(
            @AuthAdmin String ldap,
            @PathVariable Long productId,
            @RequestBody AdminProductRequest.UpdateProductRequest request
    ) {
        Product product = productService.update(productId, request.name(), request.description(), request.basePrice());
        ProductAdminFacade.ProductAdminDetailResult result = productAdminFacade.getProductDetail(productId);
        return ApiResponse.success(AdminProductResponse.ProductDetail.from(
                result.product(), result.brand(), result.inventory()));
    }

    @PatchMapping("/{productId}/status")
    @Override
    public ApiResponse<AdminProductResponse.ProductDetail> changeProductStatus(
            @AuthAdmin String ldap,
            @PathVariable Long productId,
            @RequestBody AdminProductRequest.ChangeStatusRequest request
    ) {
        Product product = productService.changeStatus(productId, request.status());
        ProductAdminFacade.ProductAdminDetailResult result = productAdminFacade.getProductDetail(productId);
        return ApiResponse.success(AdminProductResponse.ProductDetail.from(
                result.product(), result.brand(), result.inventory()));
    }

    @DeleteMapping("/{productId}")
    @Override
    public ApiResponse<Void> deleteProduct(
            @AuthAdmin String ldap,
            @PathVariable Long productId
    ) {
        productAdminFacade.deleteProduct(productId);
        return ApiResponse.success(null);
    }
}
