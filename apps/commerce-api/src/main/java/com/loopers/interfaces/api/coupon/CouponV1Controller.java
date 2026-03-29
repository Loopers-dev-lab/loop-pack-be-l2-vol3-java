package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.CouponIssueFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class CouponV1Controller {

    private final CouponFacade couponFacade;
    private final CouponIssueFacade couponIssueFacade;

    // ─────────────────────────────────────────
    // Admin APIs
    // ─────────────────────────────────────────

    @GetMapping("/api-admin/v1/coupons")
    public ApiResponse<CouponV1Dto.CouponTemplatePageResponse> getTemplates(Pageable pageable) {
        return ApiResponse.success(
            CouponV1Dto.CouponTemplatePageResponse.from(couponFacade.getTemplates(pageable))
        );
    }

    @GetMapping("/api-admin/v1/coupons/{couponId}")
    public ApiResponse<CouponV1Dto.CouponTemplateResponse> getTemplate(@PathVariable Long couponId) {
        return ApiResponse.success(
            CouponV1Dto.CouponTemplateResponse.from(couponFacade.getTemplate(couponId))
        );
    }

    @PostMapping("/api-admin/v1/coupons")
    public ApiResponse<CouponV1Dto.CouponTemplateResponse> registerTemplate(
        @Valid @RequestBody CouponV1Dto.RegisterTemplateRequest request
    ) {
        try {
            return ApiResponse.success(CouponV1Dto.CouponTemplateResponse.from(
                couponFacade.registerTemplate(
                    request.name(),
                    request.description(),
                    request.type(),
                    request.discountValue(),
                    request.expiredAt()
                )
            ));
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, e.getMessage());
        }
    }

    @PutMapping("/api-admin/v1/coupons/{couponId}")
    public ApiResponse<CouponV1Dto.CouponTemplateResponse> updateTemplate(
        @PathVariable Long couponId,
        @Valid @RequestBody CouponV1Dto.UpdateTemplateRequest request
    ) {
        try {
            return ApiResponse.success(CouponV1Dto.CouponTemplateResponse.from(
                couponFacade.updateTemplate(
                    couponId,
                    request.name(),
                    request.description(),
                    request.type(),
                    request.discountValue(),
                    request.expiredAt()
                )
            ));
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/api-admin/v1/coupons/{couponId}")
    public ApiResponse<Void> deleteTemplate(@PathVariable Long couponId) {
        couponFacade.deleteTemplate(couponId);
        return ApiResponse.success(null);
    }

    @GetMapping("/api-admin/v1/coupons/{couponId}/issues")
    public ApiResponse<CouponV1Dto.CouponIssuePageResponse> getIssues(
        @PathVariable Long couponId,
        Pageable pageable
    ) {
        return ApiResponse.success(
            CouponV1Dto.CouponIssuePageResponse.from(couponFacade.getIssues(couponId, pageable))
        );
    }

    // ─────────────────────────────────────────
    // User APIs
    // ─────────────────────────────────────────

    @PostMapping("/api/v1/coupons/{couponId}/issue")
    public ApiResponse<CouponV1Dto.CouponIssueResponse> issue(
        @RequestHeader("X-Loopers-User-Id") Long userId,
        @PathVariable Long couponId
    ) {
        return ApiResponse.success(
            CouponV1Dto.CouponIssueResponse.from(couponFacade.issue(userId, couponId))
        );
    }

    @GetMapping("/api/v1/users/me/coupons")
    public ApiResponse<CouponV1Dto.MyCouponPageResponse> getMyCoupons(
        @RequestHeader("X-Loopers-User-Id") Long userId,
        Pageable pageable
    ) {
        return ApiResponse.success(
            CouponV1Dto.MyCouponPageResponse.from(couponFacade.getMyCoupons(userId, pageable))
        );
    }

    @PostMapping("/api/v1/coupons/{couponId}/issue-async")
    public ApiResponse<CouponV1Dto.CouponIssueRequestResponse> requestIssue(
        @RequestHeader("X-Loopers-User-Id") Long userId,
        @PathVariable Long couponId
    ) {
        return ApiResponse.success(
            CouponV1Dto.CouponIssueRequestResponse.from(couponIssueFacade.requestIssue(userId, couponId))
        );
    }

    @GetMapping("/api/v1/coupons/issue-requests/{requestId}")
    public ApiResponse<CouponV1Dto.CouponIssueRequestResponse> getIssueRequestStatus(
        @PathVariable Long requestId
    ) {
        return ApiResponse.success(
            CouponV1Dto.CouponIssueRequestResponse.from(couponIssueFacade.getIssueRequestStatus(requestId))
        );
    }
}
