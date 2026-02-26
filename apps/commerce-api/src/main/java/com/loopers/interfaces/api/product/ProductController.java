package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductQueryUseCase;
import com.loopers.interfaces.api.common.PageResponse;
import com.loopers.interfaces.api.product.dto.ProductDetailResponse;
import com.loopers.interfaces.api.product.dto.ProductSummaryResponse;
import com.loopers.domain.model.common.PageResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductQueryUseCase productQueryUseCase;

    public ProductController(ProductQueryUseCase productQueryUseCase) {
        this.productQueryUseCase = productQueryUseCase;
    }

    @GetMapping
    public ResponseEntity<PageResponse<ProductSummaryResponse>> getProducts(
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResult<ProductQueryUseCase.ProductSummaryInfo> products =
                productQueryUseCase.getProducts(brandId, sort, page, size);
        return ResponseEntity.ok(PageResponse.from(products, ProductSummaryResponse::from));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ProductDetailResponse> getProduct(@PathVariable Long productId) {
        ProductQueryUseCase.ProductDetailInfo info = productQueryUseCase.getProduct(productId);
        return ResponseEntity.ok(ProductDetailResponse.from(info));
    }
}
