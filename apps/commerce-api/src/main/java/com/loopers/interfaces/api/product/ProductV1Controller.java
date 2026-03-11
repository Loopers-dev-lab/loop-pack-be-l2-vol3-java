package com.loopers.interfaces.api.product;

import com.loopers.application.product.CreateProductCommand;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductSearchCommand;
import com.loopers.domain.product.ProductSortType;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller {

    private final ProductFacade productFacade;

    @PostMapping
    public ApiResponse<ProductV1Dto.CreateProductResponse> createProducts(
        @Valid @RequestBody ProductV1Dto.CreateProductRequest request
    ) {
        CreateProductCommand command = new CreateProductCommand(
            request.toProductItems()
        );
        List<ProductInfo> productInfos = productFacade.createProducts(command);
        return ApiResponse.success(ProductV1Dto.CreateProductResponse.from(productInfos));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductV1Dto.ProductDetailResponse> getProduct(
        @PathVariable Long productId
    ) {
        ProductInfo info = productFacade.getProduct(productId);
        return ApiResponse.success(ProductV1Dto.ProductDetailResponse.from(info));
    }

    @GetMapping
    public ApiResponse<List<ProductV1Dto.ProductResponse>> getProducts(
        @RequestParam(required = false) Long brandId,
        @RequestParam(defaultValue = "LATEST") ProductSortType sortType,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        ProductSearchCommand command = new ProductSearchCommand(
            brandId,
            sortType,
            page,
            size
        );
        List<ProductInfo> productInfos = productFacade.getProducts(command);
        List<ProductV1Dto.ProductResponse> response = productInfos.stream()
            .map(ProductV1Dto.ProductResponse::from)
            .toList();
        return ApiResponse.success(response);
    }
}
