package com.loopers.interfaces.api.product;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.application.product.ProductApplicationService;
import com.loopers.domain.PageResult;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductSortType;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/products")
public class AdminProductV1Controller implements AdminProductV1ApiSpec {

    private final ProductApplicationService productApplicationService;
    private final BrandApplicationService brandApplicationService;

    @PostMapping
    @Override
    public ApiResponse<AdminProductV1Dto.ProductResponse> create(@Valid @RequestBody AdminProductV1Dto.CreateRequest request) {
        Product product = productApplicationService.register(request.brandId(), request.name(), request.price(), request.stock());
        Brand brand = brandApplicationService.getById(product.getBrandId());
        return ApiResponse.success(AdminProductV1Dto.ProductResponse.from(product, brand));
    }

    @GetMapping
    @Override
    public ApiResponse<AdminProductV1Dto.ProductPageResponse> getAll(
        @RequestParam(required = false) Long brandId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        PageResult<Product> result = productApplicationService.getAll(brandId, ProductSortType.LATEST, page, size);
        Set<Long> brandIds = result.items().stream()
            .map(Product::getBrandId)
            .collect(Collectors.toSet());
        Map<Long, Brand> brandMap = brandApplicationService.getByIds(brandIds);
        return ApiResponse.success(AdminProductV1Dto.ProductPageResponse.from(result, brandMap));
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<AdminProductV1Dto.ProductResponse> getById(@PathVariable Long productId) {
        Product product = productApplicationService.getById(productId);
        Brand brand = brandApplicationService.getById(product.getBrandId());
        return ApiResponse.success(AdminProductV1Dto.ProductResponse.from(product, brand));
    }

    @PutMapping("/{productId}")
    @Override
    public ApiResponse<AdminProductV1Dto.ProductResponse> update(
        @PathVariable Long productId,
        @Valid @RequestBody AdminProductV1Dto.UpdateRequest request
    ) {
        Product product = productApplicationService.update(productId, request.name(), request.price(), request.stock());
        Brand brand = brandApplicationService.getById(product.getBrandId());
        return ApiResponse.success(AdminProductV1Dto.ProductResponse.from(product, brand));
    }

    @DeleteMapping("/{productId}")
    @Override
    public ApiResponse<Void> delete(@PathVariable Long productId) {
        productApplicationService.delete(productId);
        return ApiResponse.success();
    }
}
