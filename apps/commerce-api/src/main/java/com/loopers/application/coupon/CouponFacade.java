package com.loopers.application.coupon;

import com.loopers.application.event.CouponIssueRequestedEvent;
import com.loopers.application.user.UserService;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.user.User;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CouponFacade {

    private final CouponService couponService;
    private final IssuedCouponService issuedCouponService;
    private final UserService userService;
    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final ApplicationEventPublisher eventPublisher;

    // Command

    @Transactional
    public CouponInfo registerCoupon(CouponCommand.Register command) {
        Coupon coupon = couponService.register(command);
        return CouponInfo.from(coupon);
    }

    @Transactional
    public void deleteCoupon(Long couponId) {
        couponService.delete(couponId);
    }

    @Transactional
    public IssuedCouponInfo issueCoupon(Long couponId, Long userId) {
        // -- 1단계: 검증 (읽기만) --
        Coupon coupon = couponService.getActiveCoupon(couponId);
        coupon.validateIssuable();

        // -- 2단계: 상태 변경 --
        IssuedCoupon issuedCoupon = issuedCouponService.issue(IssuedCouponCommand.Issue.from(coupon, userId));
        couponService.issue(couponId);  // 원자적 UPDATE

        return IssuedCouponInfo.from(issuedCoupon);
    }

    @Transactional
    public CouponIssueRequestInfo issueAsync(Long couponId, Long userId) {
        couponService.validateActiveCoupon(couponId);

        Optional<CouponIssueRequest> existing = couponIssueRequestRepository.findByCouponIdAndUserId(couponId, userId);
        if (existing.isPresent()) {
            return CouponIssueRequestInfo.from(existing.get());
        }

        String eventId = UUID.randomUUID().toString();
        CouponIssueRequest request = couponIssueRequestRepository.save(
                CouponIssueRequest.create(eventId, couponId, userId));
        eventPublisher.publishEvent(new CouponIssueRequestedEvent(eventId, couponId, userId));
        return CouponIssueRequestInfo.from(request);
    }

    @Transactional(readOnly = true)
    public CouponIssueRequestInfo getIssueStatus(Long couponId, Long userId) {
        CouponIssueRequest request = couponIssueRequestRepository.findByCouponIdAndUserId(couponId, userId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "발급 요청이 존재하지 않습니다"));
        return CouponIssueRequestInfo.from(request);
    }

    @Transactional
    public CouponInfo updateInfo(Long couponId, CouponCommand.UpdateInfo command) {
        if (command.type() != null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 유형은 변경할 수 없습니다");
        }
        Coupon coupon = couponService.updateInfo(couponId, command);
        return CouponInfo.from(coupon);
    }

    // Query

    @Transactional(readOnly = true)
    public Page<CouponInfo> getCoupons(Pageable pageable) {
        Page<Coupon> coupons = couponService.findActiveCoupons(pageable);
        return coupons.map(CouponInfo::from);
    }

    @Transactional(readOnly = true)
    public CouponInfo getCoupon(Long couponId) {
        Coupon coupon = couponService.getActiveCoupon(couponId);
        return CouponInfo.from(coupon);
    }

    @Transactional(readOnly = true)
    public Page<IssuedCouponInfo> getCouponIssues(Long couponId, Pageable pageable) {
        couponService.validateActiveCoupon(couponId);
        Page<IssuedCoupon> issuedCoupons = issuedCouponService.findByCouponId(couponId, pageable);

        List<Long> userIds = issuedCoupons.getContent().stream()
                .map(IssuedCoupon::getUserId)
                .distinct()
                .toList();
        Map<Long, User> userMap = userService.findAllByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return issuedCoupons.map(ic -> IssuedCouponInfo.from(ic, userMap.get(ic.getUserId())));
    }

    @Transactional(readOnly = true)
    public Page<IssuedCouponInfo> getMyCoupons(Long userId, Pageable pageable) {
        Page<IssuedCoupon> issuedCoupons = issuedCouponService.findAllByUserId(userId, pageable);
        return issuedCoupons.map(IssuedCouponInfo::from);
    }
}
