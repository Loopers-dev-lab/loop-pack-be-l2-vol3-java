package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductDetailInfo;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.page.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class ProductController {

    private final ProductFacade productFacade;

    @GetMapping("/api/v1/products")
    public ApiResponse<PageResponse<ProductDto.ListResponse>> getList(@RequestParam(name = "brandId", required = false) Long brandId,
                                                                      @RequestParam(name = "sort", defaultValue = "latest") String sort,
                                                                      @RequestParam(name = "page", defaultValue = "0") int page,
                                                                      @RequestParam(name = "size", defaultValue = "20") int size) {

        PageRequest pageRequest = PageRequest.of(page, size);
        PageResponse<ProductInfo> productInfos = productFacade.getList(pageRequest, brandId, sort);
        PageResponse<ProductDto.ListResponse> response = productInfos.map(ProductDto.ListResponse::from);
        return ApiResponse.success(response);
    }

    @GetMapping("/api/v1/products/{productId}")
    public ApiResponse<ProductDto.DetailResponse> getDetail(@PathVariable Long productId) {

        ProductDetailInfo productDetailInfo = productFacade.getDetail(productId);
        return ApiResponse.success(ProductDto.DetailResponse.from(productDetailInfo));
    }
}
