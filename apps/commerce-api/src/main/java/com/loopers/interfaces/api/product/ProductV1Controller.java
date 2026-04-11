package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.common.cursor.CursorPageResult;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductV1Controller implements ProductV1ApiSpec {

    private final ProductFacade productFacade;

    @GetMapping("/{productId}")
    @Override
    public ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> getProduct(
            @PathVariable String productId,
            @org.springframework.web.bind.annotation.RequestHeader(name = "X-USER-ID", required = false) Long memberId
    ) {
        ProductInfo info = productFacade.getProduct(productId, memberId);
        return ResponseEntity.ok(ApiResponse.success(ProductV1Dto.ProductResponse.from(info)));
    }

    @PutMapping("/{productId}")
    @Override
    public ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> updateProduct(
            @PathVariable String productId,
            @Valid @RequestBody ProductV1Dto.UpdateProductRequest request
    ) {
        ProductInfo info = productFacade.updateProduct(
                productId,
                request.productName(),
                request.price(),
                request.stockQuantity()
        );
        return ResponseEntity.ok(ApiResponse.success(ProductV1Dto.ProductResponse.from(info)));
    }

    @PostMapping
    @Override
    public ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> createProduct(
            @Valid @RequestBody ProductV1Dto.CreateProductRequest request
    ) {
        ProductInfo info = productFacade.createProduct(
                request.productId(),
                request.brandId(),
                request.productName(),
                request.price(),
                request.stockQuantity()
        );

        ProductV1Dto.ProductResponse response = ProductV1Dto.ProductResponse.from(info);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping
    @Override
    public ResponseEntity<ApiResponse<ProductV1Dto.ProductListResponse>> getProducts(
            @RequestParam(required = false) String brandId,
            @RequestParam(required = false, defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ProductInfo> productPage = productFacade.getProducts(brandId, sort, pageable);

        ProductV1Dto.ProductListResponse response = ProductV1Dto.ProductListResponse.from(productPage);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/cursor")
    @Override
    public ResponseEntity<ApiResponse<ProductV1Dto.CursorListResponse>> getProductsByCursor(
            @RequestParam(required = false) String brandId,
            @RequestParam(required = false, defaultValue = "latest") String sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int size
    ) {
        CursorPageResult<ProductInfo> result = productFacade.getProductsByCursor(brandId, sort, cursor, size);
        return ResponseEntity.ok(ApiResponse.success(ProductV1Dto.CursorListResponse.from(result)));
    }

    @DeleteMapping("/{productId}")
    @Override
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable String productId) {
        productFacade.deleteProduct(productId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
