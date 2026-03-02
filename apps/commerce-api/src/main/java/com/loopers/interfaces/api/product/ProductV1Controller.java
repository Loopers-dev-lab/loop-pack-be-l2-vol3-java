package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductV1Controller {

    private final ProductFacade productFacade;

    @GetMapping
    public ApiResponse<ProductV1Dto.PageResponse> getProducts(
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false, defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = createPageable(sort, page, size);
        
        Page<ProductInfo> productInfos = brandId != null
                ? productFacade.getProductsByBrandId(brandId, pageable)
                : productFacade.getProducts(pageable);

        return ApiResponse.success(ProductV1Dto.PageResponse.from(productInfos));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductV1Dto.Response> getProduct(@PathVariable Long productId) {
        ProductInfo productInfo = productFacade.getProduct(productId);
        return ApiResponse.success(ProductV1Dto.Response.from(productInfo));
    }

    private Pageable createPageable(String sort, int page, int size) {
        return switch (sort) {
            case "price_asc" -> PageRequest.of(page, size, Sort.by("price").ascending());
            case "likes_desc" -> PageRequest.of(page, size, Sort.by("likesCount").descending());
            default -> PageRequest.of(page, size, Sort.by("createdAt").descending()); // latest
        };
    }
}
