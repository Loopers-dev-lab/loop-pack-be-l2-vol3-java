package com.loopers.interfaces.apiadmin;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.page.PageQuery;
import com.loopers.support.page.PagedResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자 쿠폰 API V1 컨트롤러.
 */
@RestController
@RequestMapping("/api-admin/v1/coupons")
@RequiredArgsConstructor
public class AdminCouponV1Controller {

    private final CouponService couponService;

    /**
     * 쿠폰 템플릿을 등록한다.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<AdminCouponV1Dto.CouponResponse>> createCoupon(
            @Valid @RequestBody AdminCouponV1Dto.CouponRequest request) {
        CouponModel coupon = couponService.createCoupon(
                request.getName(), request.getType(), request.getValue(),
                request.getMinOrderAmount(), request.getExpiredAt(), request.getMaxQuantity());
        return ResponseEntity.ok(ApiResponse.success(AdminCouponV1Dto.CouponResponse.from(coupon)));
    }

    /**
     * 쿠폰 목록을 페이징 조회한다 (삭제 포함).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResult<AdminCouponV1Dto.CouponResponse>>> getCoupons(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageQuery query = new PageQuery(page, size, "couponId", false);
        PagedResult<CouponModel> pagedResult = couponService.findAllForAdminPaged(query);
        List<AdminCouponV1Dto.CouponResponse> content = pagedResult.content().stream()
                .map(AdminCouponV1Dto.CouponResponse::from)
                .toList();
        PagedResult<AdminCouponV1Dto.CouponResponse> pageResponse = new PagedResult<>(
                content, pagedResult.page(), pagedResult.size(),
                pagedResult.totalElements(), pagedResult.totalPages());
        return ResponseEntity.ok(ApiResponse.success(pageResponse));
    }

    /**
     * 쿠폰 상세를 조회한다.
     */
    @GetMapping("/{couponId}")
    public ResponseEntity<ApiResponse<AdminCouponV1Dto.CouponResponse>> getCoupon(
            @PathVariable Long couponId) {
        CouponModel coupon = couponService.findByIdForAdmin(couponId);
        return ResponseEntity.ok(ApiResponse.success(AdminCouponV1Dto.CouponResponse.from(coupon)));
    }

    /**
     * 쿠폰을 수정한다.
     */
    @PutMapping("/{couponId}")
    public ResponseEntity<ApiResponse<AdminCouponV1Dto.CouponResponse>> updateCoupon(
            @PathVariable Long couponId,
            @Valid @RequestBody AdminCouponV1Dto.CouponRequest request) {
        CouponModel coupon = couponService.updateCoupon(
                couponId, request.getName(), request.getType(), request.getValue(),
                request.getMinOrderAmount(), request.getExpiredAt());
        return ResponseEntity.ok(ApiResponse.success(AdminCouponV1Dto.CouponResponse.from(coupon)));
    }

    /**
     * 쿠폰을 소프트 삭제한다.
     */
    @DeleteMapping("/{couponId}")
    public ResponseEntity<ApiResponse<Object>> deleteCoupon(@PathVariable Long couponId) {
        couponService.deleteCoupon(couponId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * 특정 쿠폰의 발급 내역을 페이징 조회한다.
     */
    @GetMapping("/{couponId}/issues")
    public ResponseEntity<ApiResponse<PagedResult<AdminCouponV1Dto.IssueHistoryResponse>>> getIssueHistory(
            @PathVariable Long couponId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageQuery query = new PageQuery(page, size, "userCouponId", false);
        PagedResult<UserCouponModel> pagedResult = couponService.findIssueHistoryPaged(couponId, query);
        List<AdminCouponV1Dto.IssueHistoryResponse> content = pagedResult.content().stream()
                .map(AdminCouponV1Dto.IssueHistoryResponse::from)
                .toList();
        PagedResult<AdminCouponV1Dto.IssueHistoryResponse> pageResponse = new PagedResult<>(
                content, pagedResult.page(), pagedResult.size(),
                pagedResult.totalElements(), pagedResult.totalPages());
        return ResponseEntity.ok(ApiResponse.success(pageResponse));
    }
}
