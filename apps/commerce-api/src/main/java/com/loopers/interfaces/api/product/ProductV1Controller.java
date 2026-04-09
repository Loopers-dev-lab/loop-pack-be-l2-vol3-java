package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductDetailInfo;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.application.ranking.RankingFacade;
import com.loopers.domain.member.Member;
import com.loopers.domain.product.ProductSearchCondition;
import com.loopers.domain.product.ProductSortType;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.LoginMember;
import com.loopers.interfaces.api.product.dto.ProductV1Dto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 상품 API V1 컨트롤러 (목록/상세 조회, 좋아요)
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller {

    private final ProductFacade productFacade;
    private final RankingFacade rankingFacade;

    /**
     * 상품 목록 조회 - Redis Cache-Aside 적용.
     */
    @GetMapping
    public ApiResponse<Page<ProductV1Dto.ProductResponse>> getProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ProductSortType sort,
            @RequestParam(required = false) Long brandId,
            Pageable pageable) {
        ProductSearchCondition condition = ProductSearchCondition.of(keyword, sort, brandId);
        Page<ProductInfo> products = productFacade.getProducts(condition, pageable);
        return ApiResponse.success(products.map(ProductV1Dto.ProductResponse::from));
    }

    /**
     * [TEST] 상품 목록 조회 - 로컬 캐시 적용
     */
    @GetMapping("/local-cache")
    public ApiResponse<Page<ProductV1Dto.ProductResponse>> getProductsWithLocalCache(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ProductSortType sort,
            @RequestParam(required = false) Long brandId,
            Pageable pageable) {
        ProductSearchCondition condition = ProductSearchCondition.of(keyword, sort, brandId);
        Page<ProductInfo> products = productFacade.getProductsWithLocalCache(condition, pageable);
        return ApiResponse.success(products.map(ProductV1Dto.ProductResponse::from));
    }

    /**
     * [TEST] 상품 목록 조회 - 캐시 미적용
     */
    @GetMapping("/no-cache")
    public ApiResponse<Page<ProductV1Dto.ProductResponse>> getProductsNoCache(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ProductSortType sort,
            @RequestParam(required = false) Long brandId,
            Pageable pageable) {
        ProductSearchCondition condition = ProductSearchCondition.of(keyword, sort, brandId);
        Page<ProductInfo> products = productFacade.getProductsNoCache(condition, pageable);
        return ApiResponse.success(products.map(ProductV1Dto.ProductResponse::from));
    }

    /**
     * 상품 상세 조회 - Redis Cache-Aside 적용.
     * 상품 조회 시 ProductViewedEvent 를 발행하여 Kafka 통계 파이프라인에 전달한다.
     * dailyRank 는 Redis ZSET 에서 매번 조회한다 (캐싱 대상 아님).
     */
    @GetMapping("/{productId}")
    public ApiResponse<ProductV1Dto.ProductDetailResponse> getProduct(
            @LoginMember(required = false) Member member,
            @PathVariable Long productId) {
        Long memberId = member != null ? member.getId() : null;
        ProductDetailInfo info = productFacade.getProduct(productId, memberId);
        Long dailyRank = resolveDailyRankSafely(productId);
        return ApiResponse.success(ProductV1Dto.ProductDetailResponse.from(info, dailyRank));
    }

    /**
     * [TEST] 상품 상세 조회 - 로컬 캐시 적용
     *
     * dailyRank 는 Redis ZSET 에서 매번 조회한다 (캐싱 대상 아님).
     */
    @GetMapping("/{productId}/local-cache")
    public ApiResponse<ProductV1Dto.ProductDetailResponse> getProductWithLocalCache(@PathVariable Long productId) {
        ProductDetailInfo info = productFacade.getProductWithLocalCache(productId);
        Long dailyRank = resolveDailyRankSafely(productId);
        return ApiResponse.success(ProductV1Dto.ProductDetailResponse.from(info, dailyRank));
    }

    /**
     * [TEST] 상품 상세 조회 - 캐시 미적용
     */
    @GetMapping("/{productId}/no-cache")
    public ApiResponse<ProductV1Dto.ProductDetailResponse> getProductNoCache(@PathVariable Long productId) {
        ProductDetailInfo info = productFacade.getProductNoCache(productId);
        Long dailyRank = resolveDailyRankSafely(productId);
        return ApiResponse.success(ProductV1Dto.ProductDetailResponse.from(info, dailyRank));
    }

    // Redis 장애가 상품 상세 조회 전체 장애로 전파되지 않도록 dailyRank 조회를 격리한다.
    // 연결 실패·타임아웃 등 RuntimeException 발생 시 null 로 폴백하여 핵심 응답은 유지한다.
    private Long resolveDailyRankSafely(Long productId) {
        try {
            return rankingFacade.getDailyRank(productId);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @PostMapping("/{productId}/likes")
    public ApiResponse<Void> like(@LoginMember Member member, @PathVariable Long productId) {
        productFacade.like(member.getId(), productId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{productId}/likes")
    public ApiResponse<Void> unlike(@LoginMember Member member, @PathVariable Long productId) {
        productFacade.unlike(member.getId(), productId);
        return ApiResponse.success(null);
    }

    @GetMapping("/me/likes")
    public ApiResponse<Page<ProductV1Dto.ProductResponse>> getLikedProducts(
            @LoginMember Member member, Pageable pageable) {
        Page<ProductInfo> products = productFacade.getLikedProducts(member.getId(), pageable);
        return ApiResponse.success(products.map(ProductV1Dto.ProductResponse::from));
    }
}
