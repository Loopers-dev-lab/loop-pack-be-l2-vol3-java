package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponEvent;
import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.page.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class CouponFacade {

    private final UserRepository userRepository;
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Long register(String name, String type, int value, Integer totalQuantity, ZonedDateTime expiredAt) {
        Coupon coupon = totalQuantity != null
                ? Coupon.of(name, type, value, totalQuantity, expiredAt)
                : Coupon.of(name, type, value, expiredAt);
        return couponRepository.save(coupon);
    }

    @Transactional
    public void update(Long couponId, String name, int value, ZonedDateTime expiredAt) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다."));

        coupon.update(name, value, expiredAt);
    }

    @Transactional
    public void delete(Long couponId) {
        couponRepository.deleteById(couponId);
    }

    @Transactional(readOnly = true)
    public PageResponse<CouponListInfo> getList(Pageable pageable) {
        return couponRepository.findAll(pageable).map(CouponListInfo::from);
    }

    @Transactional(readOnly = true)
    public CouponDetailInfo getDetail(Long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다."));

        return CouponDetailInfo.from(coupon);
    }

    @Transactional
    public Long issue(Long couponId, Long userId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다."));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 사용자입니다."));

        UserCoupon userCoupon = UserCoupon.of(coupon, user.getId());
        return userCouponRepository.save(userCoupon);
    }

    @Transactional
    public String requestIssue(Long couponId, Long userId) {
        couponRepository.findById(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다."));
        userRepository.findById(userId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 사용자입니다."));

        String requestId = UUID.randomUUID().toString();
        couponIssueRequestRepository.save(CouponIssueRequest.of(requestId, couponId, userId));
        eventPublisher.publishEvent(new CouponEvent.IssueRequested(requestId, couponId, userId));
        return requestId;
    }

    @Transactional(readOnly = true)
    public CouponIssueRequestInfo getIssueRequestStatus(String requestId) {
        CouponIssueRequest request = couponIssueRequestRepository.findByRequestId(requestId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 발급 요청입니다."));
        return CouponIssueRequestInfo.from(request);
    }

    @Transactional(readOnly = true)
    public List<MyCouponInfo> getMyCouponList(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 사용자입니다."));

        List<UserCoupon> myCoupons = userCouponRepository.findAllByUserId(user.getId());
        return myCoupons.stream().map(MyCouponInfo::from).toList();
    }
}
