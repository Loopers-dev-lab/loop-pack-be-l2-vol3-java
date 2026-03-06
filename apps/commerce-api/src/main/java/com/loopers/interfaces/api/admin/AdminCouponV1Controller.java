package com.loopers.interfaces.api.admin;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.CouponTemplateInfo;
import com.loopers.application.coupon.IssuedCouponInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api-admin/v1/coupons")
public class AdminCouponV1Controller implements AdminCouponV1ApiSpec {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final CouponFacade couponFacade;

    public AdminCouponV1Controller(CouponFacade couponFacade) {
        this.couponFacade = couponFacade;
    }

    @GetMapping
    @Override
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<AdminCouponV1Dto.CouponResponse>>> getCoupons(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        int safePage = Math.max(DEFAULT_PAGE, page);
        int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, 100);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        var result = couponFacade.getTemplates(pageable)
            .map(AdminCouponV1Controller::toCouponResponse);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{couponId}")
    @Override
    public ResponseEntity<ApiResponse<AdminCouponV1Dto.CouponResponse>> getCoupon(
        @PathVariable Long couponId
    ) {
        CouponTemplateInfo info = couponFacade.getTemplate(couponId);
        return ResponseEntity.ok(ApiResponse.success(toCouponResponse(info)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ResponseEntity<ApiResponse<AdminCouponV1Dto.CouponResponse>> createCoupon(
        @Valid @RequestBody AdminCouponV1Dto.CreateCouponRequest request
    ) {
        var info = couponFacade.registerTemplate(
            request.name(),
            request.type(),
            request.value(),
            request.minOrderAmount(),
            request.expiredAt()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(toCouponResponse(info)));
    }

    @PutMapping("/{couponId}")
    @Override
    public ResponseEntity<ApiResponse<AdminCouponV1Dto.CouponResponse>> updateCoupon(
        @PathVariable Long couponId,
        @Valid @RequestBody AdminCouponV1Dto.UpdateCouponRequest request
    ) {
        var info = couponFacade.updateTemplate(
            couponId,
            request.name(),
            request.type(),
            request.value(),
            request.minOrderAmount(),
            request.expiredAt()
        );
        return ResponseEntity.ok(ApiResponse.success(toCouponResponse(info)));
    }

    @DeleteMapping("/{couponId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Override
    public ResponseEntity<ApiResponse<Void>> deleteCoupon(
        @PathVariable Long couponId
    ) {
        couponFacade.deleteTemplate(couponId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{couponId}/issues")
    @Override
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<AdminCouponV1Dto.IssuedCouponResponse>>> getCouponIssues(
        @PathVariable Long couponId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        int safePage = Math.max(DEFAULT_PAGE, page);
        int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, 100);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        var result = couponFacade.getIssueHistory(couponId, pageable)
            .map(AdminCouponV1Controller::toIssuedResponse);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private static AdminCouponV1Dto.CouponResponse toCouponResponse(CouponTemplateInfo info) {
        return new AdminCouponV1Dto.CouponResponse(
            info.id(),
            info.name(),
            info.type(),
            info.value(),
            info.minOrderAmount(),
            info.expiredAt()
        );
    }

    private static AdminCouponV1Dto.IssuedCouponResponse toIssuedResponse(IssuedCouponInfo info) {
        return new AdminCouponV1Dto.IssuedCouponResponse(
            info.id(),
            info.couponId(),
            info.status(),
            info.expiredAt(),
            info.usedAt(),
            info.createdAt()
        );
    }
}
