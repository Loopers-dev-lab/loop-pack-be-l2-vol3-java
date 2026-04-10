package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponIssueFacade;
import com.loopers.domain.coupon.CouponIssueResultModel;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.user.UserModel;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 쿠폰 고객 API V1 컨트롤러.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CouponV1Controller {

    private final CouponService couponService;
    private final CouponIssueFacade couponIssueFacade;

    /**
     * 쿠폰을 발급한다.
     *
     * @param user     인증된 사용자
     * @param couponId 쿠폰 템플릿 ID
     * @return 발급된 쿠폰 정보 (HTTP 200)
     */
    @PostMapping("/coupons/{couponId}/issue")
    public ResponseEntity<ApiResponse<CouponV1Dto.UserCouponResponse>> issueCoupon(
            @AuthUser UserModel user,
            @PathVariable Long couponId) {
        UserCouponModel userCoupon = couponService.issueCoupon(user.getUserId(), couponId);
        CouponModel coupon = couponService.findByIdForAdmin(couponId);
        return ResponseEntity.ok(ApiResponse.success(CouponV1Dto.UserCouponResponse.from(userCoupon, coupon)));
    }

    /**
     * 내 쿠폰 목록을 조회한다.
     *
     * @param user 인증된 사용자
     * @return 발급된 쿠폰 목록 (HTTP 200)
     */
    @GetMapping("/users/me/coupons")
    public ResponseEntity<ApiResponse<List<CouponV1Dto.UserCouponResponse>>> getMyCoupons(
            @AuthUser UserModel user) {
        List<UserCouponModel> userCoupons = couponService.findMyCoupons(user.getUserId());

        List<Long> couponIds = userCoupons.stream().map(UserCouponModel::getCouponId).toList();
        Map<Long, CouponModel> couponMap = couponService.findAllByIds(couponIds).stream()
                .collect(Collectors.toMap(CouponModel::getCouponId, Function.identity()));

        List<CouponV1Dto.UserCouponResponse> response = userCoupons.stream()
                .filter(uc -> couponMap.containsKey(uc.getCouponId()))
                .map(uc -> CouponV1Dto.UserCouponResponse.from(uc, couponMap.get(uc.getCouponId())))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 선착순 쿠폰 발급을 요청한다.
     *
     * @param user     인증된 사용자
     * @param couponId 쿠폰 ID
     * @return 요청 ID (HTTP 200)
     */
    @PostMapping("/coupons/{couponId}/rush-issue")
    public ResponseEntity<ApiResponse<CouponV1Dto.RushIssueResponse>> rushIssue(
            @AuthUser UserModel user,
            @PathVariable Long couponId) {
        String requestId = couponIssueFacade.requestRushIssue(user.getUserId(), couponId);
        return ResponseEntity.ok(ApiResponse.success(new CouponV1Dto.RushIssueResponse(requestId)));
    }

    /**
     * 선착순 쿠폰 발급 결과를 조회한다.
     *
     * @param requestId 요청 ID
     * @return 발급 결과 (HTTP 200, 처리 중이면 null data)
     */
    @GetMapping("/coupons/issue-result/{requestId}")
    public ResponseEntity<ApiResponse<CouponV1Dto.IssueResultResponse>> getIssueResult(
            @PathVariable String requestId) {
        CouponIssueResultModel result = couponIssueFacade.getIssueResult(requestId);
        return ResponseEntity.ok(ApiResponse.success(CouponV1Dto.IssueResultResponse.from(result)));
    }
}
