package com.loopers.interfaces.api.product;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.application.product.ProductApplicationService;
import com.loopers.domain.PageResult;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductSortType;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller implements ProductV1ApiSpec {

    private final ProductApplicationService productApplicationService;
    private final BrandApplicationService brandApplicationService;

    @GetMapping
    @Override
    public ApiResponse<ProductV1Dto.ProductPageResponse> getAll(
        @RequestParam(required = false) Long brandId,
        @RequestParam(defaultValue = "latest") String sort,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        PageResult<Product> result = productApplicationService.getAll(brandId, ProductSortType.from(sort), page, size);
        Set<Long> brandIds = result.items().stream()
            .map(Product::getBrandId)
            .collect(Collectors.toSet());
        Map<Long, Brand> brandMap = brandApplicationService.getByIds(brandIds);
        return ApiResponse.success(ProductV1Dto.ProductPageResponse.from(result, brandMap));
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductV1Dto.ProductResponse> getById(@PathVariable Long productId) {
        Product product = productApplicationService.getById(productId);
        Brand brand = brandApplicationService.getById(product.getBrandId());
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(product, brand));
    }
}
