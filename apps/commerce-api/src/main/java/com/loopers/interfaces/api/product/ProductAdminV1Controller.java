package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductAdminFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.interfaces.api.ApiResponse;
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
public class ProductAdminV1Controller implements ProductAdminV1ApiSpec {

    private final ProductAdminFacade productAdminFacade;

    @PostMapping
    @Override
    public ApiResponse<ProductAdminV1Dto.ProductResponse> registerProduct(
            @RequestBody ProductAdminV1Dto.RegisterRequest request) {
        return ApiResponse.success(ProductAdminV1Dto.ProductResponse.from(
                productAdminFacade.register(request.toCommand())));
    }

    @GetMapping("/{id}")
    @Override
    public ApiResponse<ProductAdminV1Dto.ProductResponse> getProductDetails(
            @PathVariable long id) {
        return ApiResponse.success(ProductAdminV1Dto.ProductResponse.from(
                productAdminFacade.findById(id)));
    }

    @GetMapping
    @Override
    public ApiResponse<ProductAdminV1Dto.ProductListResponse> getProducts(
            @RequestParam(required = false) Long brandId,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, ProductSortType.from(sort).toSort());
        Page<ProductInfo> result = productAdminFacade.findAll(brandId, pageable);
        return ApiResponse.success(ProductAdminV1Dto.ProductListResponse.from(result));
    }

    @PutMapping("/{id}")
    @Override
    public ApiResponse<ProductAdminV1Dto.ProductResponse> updateProduct(
            @PathVariable long id,
            @RequestBody ProductAdminV1Dto.UpdateRequest request) {
        return ApiResponse.success(ProductAdminV1Dto.ProductResponse.from(
                productAdminFacade.update(request.toCommand(id))));
    }

    @DeleteMapping("/{id}")
    @Override
    public ApiResponse<Void> deleteProduct(@PathVariable long id) {
        productAdminFacade.delete(id);
        return ApiResponse.success(null);
    }
}
