package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductDetailInfo;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductListInfo;
import com.loopers.application.ranking.RankingFacade;
import com.loopers.domain.product.SortCondition;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller implements ProductV1ApiSpec {

    private final ProductFacade productFacade;
    private final RankingFacade rankingFacade;

    @GetMapping
    @Override
    public ApiResponse<List<ProductV1Dto.ProductListResponse>> getProductList(
        @RequestParam(defaultValue = "latest") SortCondition sort
    ) {
        List<ProductListInfo> productList = productFacade.getProductList(sort);
        List<ProductV1Dto.ProductListResponse> response = productList.stream()
            .map(ProductV1Dto.ProductListResponse::from)
            .toList();
        return ApiResponse.success(response);
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductV1Dto.ProductDetailResponse> getProductDetail(
        @PathVariable Long productId
    ) {
        ProductDetailInfo info = productFacade.getProductDetail(productId);

        // 비핵심 부수효과 — 실패해도 상품 조회 응답은 정상 반환 (fail-open)
        try { productFacade.publishViewedEvent(productId); }
        catch (Exception e) { log.warn("[ProductDetail] VIEWED 이벤트 발행 실패 productId={}", productId, e); }

        Long rank = null;
        try { rank = rankingFacade.getRank(productId); }
        catch (Exception e) { log.warn("[ProductDetail] 랭킹 조회 실패 productId={}", productId, e); }

        return ApiResponse.success(ProductV1Dto.ProductDetailResponse.from(info, rank));
    }
}
