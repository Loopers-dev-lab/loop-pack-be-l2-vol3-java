package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserCouponService {

    private final CouponService couponService;
    private final UserCouponRepository userCouponRepository;

    @Transactional
    public UserCouponModel issue(Long userId, Long couponId) {
        CouponModel coupon = couponService.getCouponForUpdate(couponId);

        if (coupon.isExpired()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰은 발급할 수 없습니다.");
        }

        if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급된 쿠폰입니다.");
        }

        coupon.reserveIssue();
        UserCouponModel userCoupon = new UserCouponModel(userId, coupon);
        try {
            return userCouponRepository.save(userCoupon);
        } catch (DataIntegrityViolationException e) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급된 쿠폰입니다.");
        }
    }

    @Transactional(readOnly = true)
    public List<UserCouponModel> getMyCoupons(Long userId) {
        return userCouponRepository.findAllByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Page<UserCouponModel> getCouponIssues(Long couponId, Pageable pageable) {
        couponService.getCoupon(couponId);
        return userCouponRepository.findAllByCouponId(couponId, pageable);
    }

    @Transactional
    public UserCouponModel getAvailableUserCouponForUse(Long userId, Long userCouponId) {
        return userCouponRepository.findByIdAndUserIdForUpdate(userCouponId, userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않거나 소유하지 않은 쿠폰입니다."));
    }

    @Transactional
    public long calculateDiscountAmount(UserCouponModel userCoupon, long orderAmount) {
        return userCoupon.calculateDiscountAmount(orderAmount);
    }

    @Transactional
    public void markUsed(UserCouponModel userCoupon, Long orderId, long orderAmount) {
        userCoupon.use(orderId, orderAmount);
    }
}
