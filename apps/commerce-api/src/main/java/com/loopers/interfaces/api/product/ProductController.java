package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.product.ProductSortType;
import com.loopers.interfaces.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController implements ProductApiSpec {

    private final ProductFacade productFacade;

    public ProductController(ProductFacade productFacade) {
        this.productFacade = productFacade;
    }

    @GetMapping
    @Override
    public ApiResponse<ProductResponse.ProductListResponse> getProducts(
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) ProductSortType sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        ProductFacade.ProductListResult result = productFacade.getDisplayableProducts(brandId, sort, page, size);

        List<ProductResponse.ProductSummary> summaries = result.products().stream()
                .map(ProductResponse.ProductSummary::from)
                .toList();

        return ApiResponse.success(new ProductResponse.ProductListResponse(
                summaries, result.page(), result.size(), result.totalElements(), result.totalPages()));
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductResponse.ProductDetail> getProduct(@PathVariable Long productId) {
        ProductFacade.ProductDetailResult result = productFacade.getProductDetail(productId);
        ProductInfo product = result.product();
        String brandName = result.brand().name();

        return ApiResponse.success(ProductResponse.ProductDetail.from(product, brandName));
    }
}
