package com.loopers.interfaces.api.experiment;

import com.loopers.application.product.ProductExperimentFacade;
import com.loopers.application.product.ProductExperimentFacade.CursorResult;
import com.loopers.application.product.ProductExperimentInfo;
import com.loopers.application.product.ProductInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.CursorResponse;
import com.loopers.interfaces.api.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/experiment/products")
@RequiredArgsConstructor
public class ProductExperimentController {

    private final ProductExperimentFacade experimentFacade;

    // ===== Detail =====

    @GetMapping("/v1/{productId}")
    public ApiResponse<ProductExperimentV1Dto.DetailResponse> detailV1(
            @PathVariable Long productId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId
    ) {
        ProductExperimentInfo info = experimentFacade.getDetailV1(productId, userId);
        return ApiResponse.success(ProductExperimentV1Dto.DetailResponse.from(info));
    }

    @GetMapping("/v2/{productId}")
    public ApiResponse<ProductExperimentV1Dto.DetailResponse> detailV2(
            @PathVariable Long productId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId
    ) {
        ProductExperimentInfo info = experimentFacade.getDetailV2(productId, userId);
        return ApiResponse.success(ProductExperimentV1Dto.DetailResponse.from(info));
    }

    @GetMapping("/v3/{productId}")
    public ApiResponse<ProductExperimentV1Dto.DetailResponse> detailV3(
            @PathVariable Long productId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId
    ) {
        ProductExperimentInfo info = experimentFacade.getDetailV3(productId, userId);
        return ApiResponse.success(ProductExperimentV1Dto.DetailResponse.from(info));
    }

    // ===== List Offset =====

    @GetMapping("/v1/offset")
    public ApiResponse<PageResponse<ProductExperimentV1Dto.ListResponse>> listOffsetV1(
            @Valid ProductExperimentRequest.ListOffset request
    ) {
        Page<ProductInfo> result = experimentFacade.getListOffsetV1(request.brandId(), request.toPageable());
        return ApiResponse.success(PageResponse.from(result, ProductExperimentV1Dto.ListResponse::from));
    }

    @GetMapping("/v2/offset")
    public ApiResponse<PageResponse<ProductExperimentV1Dto.ListResponse>> listOffsetV2(
            @Valid ProductExperimentRequest.ListOffset request
    ) {
        Page<ProductInfo> result = experimentFacade.getListOffsetV2(request.brandId(), request.toPageable());
        return ApiResponse.success(PageResponse.from(result, ProductExperimentV1Dto.ListResponse::from));
    }

    @GetMapping("/v3/offset")
    public ApiResponse<PageResponse<ProductExperimentV1Dto.ListResponse>> listOffsetV3(
            @Valid ProductExperimentRequest.ListOffset request
    ) {
        Page<ProductInfo> result = experimentFacade.getListOffsetV3(request.brandId(), request.toPageable());
        return ApiResponse.success(PageResponse.from(result, ProductExperimentV1Dto.ListResponse::from));
    }

    // ===== List Cursor =====

    @GetMapping("/v1/cursor")
    public ApiResponse<CursorResponse<ProductExperimentV1Dto.ListResponse>> listCursorV1(
            @Valid ProductExperimentRequest.ListCursor request
    ) {
        CursorResult result = experimentFacade.getListCursorV1(request.brandId(), request.cursor(), request.size());
        return ApiResponse.success(CursorResponse.of(
                result.content(), result.nextCursor(), result.hasNext(), request.size(),
                ProductExperimentV1Dto.ListResponse::from));
    }

    @GetMapping("/v2/cursor")
    public ApiResponse<CursorResponse<ProductExperimentV1Dto.ListResponse>> listCursorV2(
            @Valid ProductExperimentRequest.ListCursor request
    ) {
        CursorResult result = experimentFacade.getListCursorV2(request.brandId(), request.cursor(), request.size());
        return ApiResponse.success(CursorResponse.of(
                result.content(), result.nextCursor(), result.hasNext(), request.size(),
                ProductExperimentV1Dto.ListResponse::from));
    }

    @GetMapping("/v3/cursor")
    public ApiResponse<CursorResponse<ProductExperimentV1Dto.ListResponse>> listCursorV3(
            @Valid ProductExperimentRequest.ListCursor request
    ) {
        CursorResult result = experimentFacade.getListCursorV3(request.brandId(), request.cursor(), request.size());
        return ApiResponse.success(CursorResponse.of(
                result.content(), result.nextCursor(), result.hasNext(), request.size(),
                ProductExperimentV1Dto.ListResponse::from));
    }

    // ===== Stats =====

    @GetMapping("/cache-stats")
    public ApiResponse<String> cacheStats() {
        return ApiResponse.success(experimentFacade.getCacheStats());
    }
}
