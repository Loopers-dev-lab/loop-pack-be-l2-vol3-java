package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller implements ProductV1ApiSpec {

    private final ProductFacade productFacade;

    public ProductV1Controller(ProductFacade productFacade) {
        this.productFacade = productFacade;
    }

    @GetMapping("/{productId}")
    @Override
    public ResponseEntity<ApiResponse<ProductV1Dto.DetailResponse>> getProductDetail(
        @PathVariable Long productId
    ) {
        return productFacade.getProductDetail(productId)
            .map(info -> ResponseEntity.ok(ApiResponse.success(ProductV1Dto.DetailResponse.from(info))))
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: " + productId));
    }
}
