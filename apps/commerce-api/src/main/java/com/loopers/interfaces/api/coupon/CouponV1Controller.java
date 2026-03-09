package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.user.UserFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CouponV1Controller implements CouponV1ApiSpec {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final CouponFacade couponFacade;
    private final UserFacade userFacade;

    public CouponV1Controller(CouponFacade couponFacade, UserFacade userFacade) {
        this.couponFacade = couponFacade;
        this.userFacade = userFacade;
    }

    @PostMapping("/coupons/{couponId}/issue")
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ApiResponse<CouponV1Dto.IssuedCouponResponse> issueCoupon(
        @RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
        @PathVariable Long couponId
    ) {
        Long userId = userFacade.findUserIdByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "로그인이 필요합니다."));
        var info = couponFacade.issueCoupon(userId, couponId);
        return ApiResponse.success(CouponV1Dto.IssuedCouponResponse.from(info));
    }

    @GetMapping("/users/me/coupons")
    @Override
    public ApiResponse<CouponV1Dto.PagedIssuedCouponsResponse> getMyCoupons(
        @RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Long userId = userFacade.findUserIdByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "로그인이 필요합니다."));
        int safePage = Math.max(DEFAULT_PAGE, page);
        int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, 100);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        var result = couponFacade.getMyCoupons(userId, pageable);
        return ApiResponse.success(CouponV1Dto.PagedIssuedCouponsResponse.from(result));
    }
}
