package com.loopers.interfaces.api.admin;

import com.loopers.application.admin.product.AdminProductAppService;
import com.loopers.domain.product.Option;
import com.loopers.domain.product.Product;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.resolver.LoginAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/v1/products")
@RequiredArgsConstructor
public class AdminProductController {
    private final AdminProductAppService adminProductAppService;

    @PostMapping
    public ApiResponse<AdminProductDto.ProductResponse> create(
            @LoginAdmin String adminId,
            @RequestBody AdminProductDto.CreateRequest request) {
        Product product = adminProductAppService.create(request.brandId(), request.name(), request.toBasePrice());
        return ApiResponse.success(AdminProductDto.ProductResponse.from(product));
    }

    @PostMapping("/{productId}/options")
    public ApiResponse<AdminProductDto.OptionResponse> createOption(
            @LoginAdmin String adminId,
            @PathVariable Long productId,
            @RequestBody AdminProductDto.CreateOptionRequest request) {
        Option option = adminProductAppService.createOption(
                productId, request.name(), request.toAdditionalPrice(), request.stock());
        return ApiResponse.success(AdminProductDto.OptionResponse.from(option));
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminProductDto.ProductResponse> update(
            @LoginAdmin String adminId,
            @PathVariable Long id,
            @RequestBody AdminProductDto.UpdateRequest request) {
        Product product = adminProductAppService.update(id, request.name(), request.toBasePrice());
        return ApiResponse.success(AdminProductDto.ProductResponse.from(product));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @LoginAdmin String adminId,
            @PathVariable Long id) {
        adminProductAppService.delete(id);
        return ApiResponse.success(null);
    }

    @GetMapping
    public ApiResponse<AdminProductDto.ProductListResponse> getAll(@LoginAdmin String adminId) {
        List<Product> products = adminProductAppService.getAll();
        return ApiResponse.success(AdminProductDto.ProductListResponse.from(products));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminProductDto.ProductDetailResponse> getById(
            @LoginAdmin String adminId,
            @PathVariable Long id) {
        Product product = adminProductAppService.getById(id);
        List<Option> options = adminProductAppService.getOptionsByProductId(id);
        return ApiResponse.success(AdminProductDto.ProductDetailResponse.of(product, options));
    }

    @PutMapping("/{productId}/options/{optionId}/stock")
    public ApiResponse<AdminProductDto.OptionResponse> updateOptionStock(
            @LoginAdmin String adminId,
            @PathVariable Long productId,
            @PathVariable Long optionId,
            @RequestBody AdminProductDto.UpdateStockRequest request) {
        Option option = adminProductAppService.updateOptionStock(productId, optionId, request.stock());
        return ApiResponse.success(AdminProductDto.OptionResponse.from(option));
    }
}
