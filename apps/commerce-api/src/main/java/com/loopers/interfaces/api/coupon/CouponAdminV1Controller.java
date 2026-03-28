package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponAdminFacade;
import com.loopers.application.coupon.CouponInfo;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponType;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api-admin/v1/coupons")
public class CouponAdminV1Controller implements CouponAdminV1ApiSpec {

    private final CouponAdminFacade couponAdminFacade;

    @PostMapping
    @Override
    public ApiResponse<CouponAdminV1Dto.CouponResponse> register(
            @RequestBody CouponAdminV1Dto.RegisterRequest request
    ) {
        CouponInfo info = couponAdminFacade.register(
                new CouponService.RegisterCommand(
                        request.name(), request.type(), request.value(),
                        request.minOrderAmount(), request.expiredAt(), request.totalQuantity()
                )
        );
        return ApiResponse.success(toCouponResponse(info));
    }

    @GetMapping
    @Override
    public ApiResponse<Page<CouponAdminV1Dto.CouponResponse>> getAll(Pageable pageable) {
        Page<CouponAdminV1Dto.CouponResponse> responses = couponAdminFacade.getAll(pageable)
                .map(this::toCouponResponse);
        return ApiResponse.success(responses);
    }

    @GetMapping("/{couponId}")
    @Override
    public ApiResponse<CouponAdminV1Dto.CouponResponse> getById(@PathVariable Long couponId) {
        CouponInfo info = couponAdminFacade.getById(couponId);
        return ApiResponse.success(toCouponResponse(info));
    }

    @PutMapping("/{couponId}")
    @Override
    public ApiResponse<CouponAdminV1Dto.CouponResponse> modifyInfo(
            @PathVariable Long couponId,
            @RequestBody CouponAdminV1Dto.ModifyRequest request
    ) {
        CouponInfo info = couponAdminFacade.modifyInfo(couponId,
                new CouponService.ModifyCommand(
                        request.name(), request.value(), request.minOrderAmount(), request.expiredAt()
                )
        );
        return ApiResponse.success(toCouponResponse(info));
    }

    @DeleteMapping("/{couponId}")
    @Override
    public ApiResponse<Void> delete(@PathVariable Long couponId) {
        couponAdminFacade.delete(couponId);
        return ApiResponse.success(null);
    }

    @GetMapping("/{couponId}/issues")
    @Override
    public ApiResponse<Page<CouponAdminV1Dto.UserCouponResponse>> getIssues(
            @PathVariable Long couponId, Pageable pageable
    ) {
        Page<CouponAdminV1Dto.UserCouponResponse> responses = couponAdminFacade.getIssuesByCouponId(couponId, pageable)
                .map(info -> new CouponAdminV1Dto.UserCouponResponse(
                        info.userCouponId(), info.couponName(), info.type(),
                        resolveValue(info.type(), info.discountAmount(), info.discountRate()),
                        info.minOrderAmount(), info.status().name(), info.expiredAt()
                ));
        return ApiResponse.success(responses);
    }

    private CouponAdminV1Dto.CouponResponse toCouponResponse(CouponInfo info) {
        return new CouponAdminV1Dto.CouponResponse(
                info.id(), info.name(), info.type(),
                resolveValue(info.type(), info.discountAmount(), info.discountRate()),
                info.minOrderAmount(), info.expiredAt()
        );
    }

    private BigDecimal resolveValue(CouponType type, BigDecimal discountAmount, Integer discountRate) {
        return type == CouponType.FIXED ? discountAmount : BigDecimal.valueOf(discountRate);
    }
}
