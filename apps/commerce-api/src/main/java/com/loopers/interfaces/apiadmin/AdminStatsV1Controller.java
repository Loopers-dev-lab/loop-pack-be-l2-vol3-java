package com.loopers.interfaces.apiadmin;

import com.loopers.domain.stats.StatsService;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 관리자 전용 운영 통계 REST API 엔드포인트를 제공하는 컨트롤러.
 *
 * <p>주문 현황 개요, 일별 주문 통계, 인기 상품(좋아요/주문 기준), 저재고 상품 조회 기능을 관리자에게 제공한다.
 * {@link StatsService}를 직접 호출한다.</p>
 */
@RestController
@RequestMapping("/api-admin/v1/stats")
@RequiredArgsConstructor
public class AdminStatsV1Controller {

    private final StatsService statsService;

    /**
     * 기간별 주문 현황 개요(대기/취소/만료 건수)를 조회한다.
     *
     * @param startAt 조회 시작일
     * @param endAt   조회 종료일
     * @return 주문 현황 개요 응답
     */
    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<AdminStatsV1Dto.OverviewResponse>> overview(
            @RequestParam("startAt") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startAt,
            @RequestParam("endAt") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endAt) {
        var overview = statsService.getOverview(startAt, endAt);
        return ResponseEntity.ok(ApiResponse.success(AdminStatsV1Dto.OverviewResponse.from(overview)));
    }

    /**
     * 기간별 일별 주문 통계(주문 건수, 총 금액)를 조회한다.
     *
     * @param startAt 조회 시작일
     * @param endAt   조회 종료일
     * @return 일별 주문 통계 목록 응답
     */
    @GetMapping("/orders/daily")
    public ResponseEntity<ApiResponse<List<AdminStatsV1Dto.DailyOrderStatResponse>>> dailyOrderStats(
            @RequestParam("startAt") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startAt,
            @RequestParam("endAt") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endAt) {
        var stats = statsService.getDailyOrderStats(startAt, endAt);
        List<AdminStatsV1Dto.DailyOrderStatResponse> response = stats.stream()
                .map(AdminStatsV1Dto.DailyOrderStatResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 좋아요 수 기준 인기 상품 목록을 조회한다.
     *
     * @param limit 조회할 상품 수 (기본값: 10)
     * @return 좋아요 기준 인기 상품 목록 응답
     */
    @GetMapping("/products/top-liked")
    public ResponseEntity<ApiResponse<List<AdminStatsV1Dto.ProductStatResponse>>> topLikedProducts(
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        var stats = statsService.getTopLikedProducts(limit);
        List<AdminStatsV1Dto.ProductStatResponse> response = stats.stream()
                .map(AdminStatsV1Dto.ProductStatResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 주문 수 기준 인기 상품 목록을 조회한다.
     *
     * @param limit 조회할 상품 수 (기본값: 10)
     * @return 주문 기준 인기 상품 목록 응답
     */
    @GetMapping("/products/top-ordered")
    public ResponseEntity<ApiResponse<List<AdminStatsV1Dto.ProductStatResponse>>> topOrderedProducts(
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        var stats = statsService.getTopOrderedProducts(limit);
        List<AdminStatsV1Dto.ProductStatResponse> response = stats.stream()
                .map(AdminStatsV1Dto.ProductStatResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 가용 재고가 임계값 이하인 저재고 상품 목록을 조회한다.
     *
     * @param threshold 재고 임계값 (기본값: 10)
     * @return 저재고 상품 목록 응답
     */
    @GetMapping("/stocks/low")
    public ResponseEntity<ApiResponse<List<AdminStatsV1Dto.LowStockProductResponse>>> lowStockProducts(
            @RequestParam(value = "threshold", defaultValue = "10") int threshold) {
        var stocks = statsService.getLowStockProducts(threshold);
        List<AdminStatsV1Dto.LowStockProductResponse> response = stocks.stream()
                .map(AdminStatsV1Dto.LowStockProductResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
