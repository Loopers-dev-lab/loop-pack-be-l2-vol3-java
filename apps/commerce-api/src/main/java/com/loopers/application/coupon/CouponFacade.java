package com.loopers.application.coupon;

import com.loopers.application.event.ApplicationDomainEventPublisher;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponService;
import com.loopers.domain.event.CouponIssueRequestModel;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.infrastructure.event.CouponIssueRequestJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class CouponFacade {

    private final CouponService couponService;
    private final UserCouponService userCouponService;
    private final UserService userService;
    private final CouponIssueRequestJpaRepository couponIssueRequestJpaRepository;
    private final ApplicationDomainEventPublisher applicationDomainEventPublisher;

    public CouponInfo register(String name, CouponType type, Long value, Long minOrderAmount, ZonedDateTime expiredAt, Long issueLimit) {
        CouponModel coupon = couponService.register(name, type, value, minOrderAmount, expiredAt, issueLimit);
        return CouponInfo.from(coupon);
    }

    public CouponInfo update(Long couponId, String name, CouponType type, Long value, Long minOrderAmount, ZonedDateTime expiredAt, Long issueLimit) {
        CouponModel coupon = couponService.update(couponId, name, type, value, minOrderAmount, expiredAt, issueLimit);
        return CouponInfo.from(coupon);
    }

    public void delete(Long couponId) {
        couponService.delete(couponId);
    }

    public CouponInfo getCoupon(Long couponId) {
        CouponModel coupon = couponService.getCoupon(couponId);
        return CouponInfo.from(coupon);
    }

    public Page<CouponInfo> getCoupons(Pageable pageable) {
        return couponService.getAll(pageable).map(CouponInfo::from);
    }

    public UserCouponInfo issue(String loginId, String password, Long couponId) {
        UserModel user = userService.getMyInfo(loginId, password);
        UserCouponModel userCoupon = userCouponService.issue(user.getId(), couponId);
        return UserCouponInfo.from(userCoupon);
    }

    @Transactional
    public CouponIssueRequestInfo requestIssue(String loginId, String password, Long couponId) {
        UserModel user = userService.getMyInfo(loginId, password);
        couponService.getCoupon(couponId);
        CouponIssueRequestModel request = couponIssueRequestJpaRepository.save(new CouponIssueRequestModel(couponId, user.getId()));
        applicationDomainEventPublisher.publishCouponIssueRequested(request.getId(), couponId, user.getId());
        return CouponIssueRequestInfo.from(request);
    }

    @Transactional(readOnly = true)
    public CouponIssueRequestInfo getIssueRequest(String loginId, String password, Long requestId) {
        UserModel user = userService.getMyInfo(loginId, password);
        CouponIssueRequestModel request = couponIssueRequestJpaRepository.findById(requestId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 발급 요청을 찾을 수 없습니다."));
        if (!request.getUserId().equals(user.getId())) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "본인 요청만 조회할 수 있습니다.");
        }
        return CouponIssueRequestInfo.from(request);
    }

    public List<UserCouponInfo> getMyCoupons(String loginId, String password) {
        UserModel user = userService.getMyInfo(loginId, password);
        return userCouponService.getMyCoupons(user.getId()).stream()
            .map(UserCouponInfo::from)
            .toList();
    }

    public Page<UserCouponInfo> getCouponIssues(Long couponId, Pageable pageable) {
        return userCouponService.getCouponIssues(couponId, pageable).map(UserCouponInfo::from);
    }
}
