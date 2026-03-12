package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponAdminService;
import com.loopers.application.coupon.CouponTemplateResult;
import com.loopers.application.coupon.IssuedCouponResult;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 쿠폰 관리자 API.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/coupons")
public class CouponAdminV1Controller {

    private final CouponAdminService couponAdminService;

    /**
     * 쿠폰 생성.
     */
    @PostMapping
    public ApiResponse<CouponV1Dto.CouponTemplateResponseDto> createCoupon(
            @RequestBody CouponV1Dto.CreateCouponRequestDto request) {
        CouponTemplateResult result = couponAdminService.create(request.toServiceRequest());
        return ApiResponse.success(CouponV1Dto.CouponTemplateResponseDto.from(result));
    }

    /**
     * 쿠폰 목록 조회.
     */
    @GetMapping
    public ApiResponse<CouponV1Dto.CouponTemplateListResponseDto> getCoupons(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<CouponTemplateResult> resultPage = couponAdminService.findAll(PageRequest.of(page, size));

        List<CouponV1Dto.CouponTemplateResponseDto> coupons = resultPage.getContent().stream()
            .map(CouponV1Dto.CouponTemplateResponseDto::from)
            .toList();

        return ApiResponse.success(new CouponV1Dto.CouponTemplateListResponseDto(
            coupons,
            resultPage.getNumber(),
            resultPage.getSize(),
            resultPage.getTotalElements(),
            resultPage.getTotalPages()
        ));
    }

    /**
     * 쿠폰 상세 조회.
     */
    @GetMapping("/{couponId}")
    public ApiResponse<CouponV1Dto.CouponTemplateResponseDto> getCoupon(@PathVariable Long couponId) {
        CouponTemplateResult result = couponAdminService.findById(couponId);
        return ApiResponse.success(CouponV1Dto.CouponTemplateResponseDto.from(result));
    }

    /**
     * 쿠폰 삭제.
     */
    @DeleteMapping("/{couponId}")
    public ApiResponse<Object> deleteCoupon(@PathVariable Long couponId) {
        couponAdminService.delete(couponId);
        return ApiResponse.success();
    }

    /**
     * 쿠폰 발급 내역 조회.
     */
    @GetMapping("/{couponId}/issues")
    public ApiResponse<CouponV1Dto.IssuedCouponListResponseDto> getIssues(
            @PathVariable Long couponId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        List<IssuedCouponResult> issues = couponAdminService.findIssues(couponId, offset, limit);
        long totalCount = couponAdminService.countIssues(couponId);

        List<CouponV1Dto.IssuedCouponSimpleResponseDto> dtos = issues.stream()
            .map(CouponV1Dto.IssuedCouponSimpleResponseDto::from)
            .toList();

        return ApiResponse.success(new CouponV1Dto.IssuedCouponListResponseDto(dtos, totalCount));
    }
}
