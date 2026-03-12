package com.loopers.interfaces.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.dto.CouponResult;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.coupon.dto.AdminCouponV1Dto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/coupons")
public class AdminCouponV1Controller implements AdminCouponV1ApiSpec {

    private final CouponFacade couponFacade;

    @PostMapping
    @Override
    public ApiResponse<AdminCouponV1Dto.DetailResponse> register(
        @Valid @RequestBody AdminCouponV1Dto.RegisterRequest request
    ) {
        CouponResult.Detail result = couponFacade.registerCoupon(request.toCriteria());
        return ApiResponse.success(AdminCouponV1Dto.DetailResponse.from(result));
    }

    @GetMapping
    @Override
    public ApiResponse<AdminCouponV1Dto.ListResponse> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<CouponResult.Detail> couponPage = couponFacade.getCoupons(PageRequest.of(page, size));
        return ApiResponse.success(
                new AdminCouponV1Dto.ListResponse(
                        couponPage.getNumber(),
                        couponPage.getSize(),
                        couponPage.getTotalElements(),
                        couponPage.getTotalPages(),
                        couponPage.getContent().stream()
                                .map(AdminCouponV1Dto.ListItem::from)
                                .toList()));
    }

    @GetMapping("/{couponId}")
    @Override
    public ApiResponse<AdminCouponV1Dto.DetailResponse> getById(
        @PathVariable Long couponId
    ) {
        CouponResult.Detail result = couponFacade.getCoupon(couponId);
        return ApiResponse.success(AdminCouponV1Dto.DetailResponse.from(result));
    }

    @PutMapping("/{couponId}")
    @Override
    public ApiResponse<Object> update(
        @PathVariable Long couponId,
        @Valid @RequestBody AdminCouponV1Dto.UpdateRequest request
    ) {
        couponFacade.updateCoupon(couponId, request.toCriteria());
        return ApiResponse.success();
    }

    @DeleteMapping("/{couponId}")
    @Override
    public ApiResponse<Object> delete(
        @PathVariable Long couponId
    ) {
        couponFacade.deleteCoupon(couponId);
        return ApiResponse.success();
    }

    @GetMapping("/{couponId}/issues")
    @Override
    public ApiResponse<AdminCouponV1Dto.IssueListResponse> issues(
        @PathVariable Long couponId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<CouponResult.IssuedDetail> issuedPage =
                couponFacade.getIssuedCoupons(couponId, PageRequest.of(page, size));
        return ApiResponse.success(
                new AdminCouponV1Dto.IssueListResponse(
                        issuedPage.getNumber(),
                        issuedPage.getSize(),
                        issuedPage.getTotalElements(),
                        issuedPage.getTotalPages(),
                        issuedPage.getContent().stream()
                                .map(AdminCouponV1Dto.IssueListItem::from)
                                .toList()));
    }
}
