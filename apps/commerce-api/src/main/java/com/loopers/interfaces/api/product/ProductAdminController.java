package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.AdminOnly;
import com.loopers.support.page.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class ProductAdminController {

    private final ProductFacade productFacade;

    @AdminOnly
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api-admin/v1/products")
    public ApiResponse<Void> register(@RequestBody ProductAdminDto.RegisterRequest request) {
        productFacade.register(request.name(), request.description(), request.stock(), request.price(), request.brandId());
        return ApiResponse.success(null);
    }

    @AdminOnly
    @PutMapping("/api-admin/v1/products/{productId}")
    public ApiResponse<Void> update(@PathVariable Long productId,
                                    @RequestBody ProductAdminDto.UpdateRequest request) {
        productFacade.update(productId, request.name(), request.description(), request.stock(), request.price());
        return ApiResponse.success(null);
    }

    @AdminOnly
    @GetMapping("/api-admin/v1/products")
    public ApiResponse<PageResponse<ProductAdminDto.ListResponse>> getList(@RequestParam(name = "page", defaultValue = "0") int page,
                                                                           @RequestParam(name = "size", defaultValue = "20") int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        PageResponse<ProductInfo> list = productFacade.getList(pageRequest);
        PageResponse<ProductAdminDto.ListResponse> result = list.map(ProductAdminDto.ListResponse::from);
        return ApiResponse.success(result);
    }

    @AdminOnly
    @GetMapping("/api-admin/v1/products/{productId}")
    public ApiResponse<ProductAdminDto.DetailResponse> getDetail(@PathVariable Long productId) {
        return ApiResponse.success(ProductAdminDto.DetailResponse.from(productFacade.getDetail(productId).productInfo()));
    }

    @AdminOnly
    @DeleteMapping("/api-admin/v1/products/{productId}")
    public ApiResponse<Void> delete(@PathVariable Long productId) {
        productFacade.delete(productId);
        return ApiResponse.success(null);
    }
}
