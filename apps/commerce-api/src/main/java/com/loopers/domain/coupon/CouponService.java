package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final CouponIssueResultRepository couponIssueResultRepository;

    @Transactional
    public Coupon createCoupon(CreateCouponCommand command) {
        Coupon coupon = Coupon.create(command.name(), command.type(), command.value(), command.minOrderAmount(), command.expiredAt(), command.totalQuantity());
        return couponRepository.save(coupon);
    }

    public Coupon getById(Long couponId) {
        return couponRepository.findActiveById(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
    }

    public Page<Coupon> getAll(Pageable pageable) {
        return couponRepository.findAllActive(pageable);
    }

    @Transactional
    public Coupon updateCoupon(Long couponId, UpdateCouponCommand command) {
        Coupon coupon = getById(couponId);
        coupon.update(command.name(), command.type(), command.value(), command.minOrderAmount(), command.expiredAt());
        return coupon;
    }

    @Transactional
    public void deleteCoupon(Long couponId) {
        Coupon coupon = getById(couponId);
        coupon.delete();
    }

    @Transactional
    public UserCoupon issueCoupon(Long userId, Long couponId) {
        Coupon coupon = getById(couponId);

        if (coupon.isExpired()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰은 발급할 수 없습니다.");
        }

        if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.");
        }

        UserCoupon userCoupon = UserCoupon.create(userId, couponId);
        return userCouponRepository.save(userCoupon);
    }

    public List<UserCoupon> getUserCoupons(Long userId) {
        return userCouponRepository.findAllByUserId(userId);
    }

    public Page<UserCoupon> getIssuedCoupons(Long couponId, Pageable pageable) {
        return userCouponRepository.findAllByCouponId(couponId, pageable);
    }

    @Transactional
    public BigDecimal useUserCoupon(Long userCouponId, Long userId, BigDecimal orderAmount) {
        UserCoupon userCoupon = userCouponRepository.findByIdWithLock(userCouponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "발급된 쿠폰을 찾을 수 없습니다."));

        userCoupon.validateUsable(userId);

        Coupon coupon = getById(userCoupon.getCouponId());
        coupon.validateApplicable(orderAmount);

        userCoupon.use();

        return coupon.calculateDiscount(orderAmount);
    }

    @Transactional
    public void issueCouponWithQuantityControl(Long userId, Long couponId) {
        CouponIssueResult issueResult = couponIssueResultRepository.findByUserIdAndCouponId(userId, couponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "발급 요청을 찾을 수 없습니다."));

        if (issueResult.getStatus() != CouponIssueResultStatus.PROCESSING) {
            return;
        }

        try {
            Coupon coupon = getById(couponId);

            coupon.issue();

            if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
                throw new CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.");
            }

            UserCoupon userCoupon = UserCoupon.create(userId, couponId);
            userCouponRepository.save(userCoupon);
            issueResult.markSuccess();
        } catch (CoreException e) {
            issueResult.markFailed(e.getMessage());
        }
    }

    @Transactional
    public CouponIssueResult createIssueResult(Long userId, Long couponId) {
        return couponIssueResultRepository.save(CouponIssueResult.create(userId, couponId));
    }

    public CouponIssueResult getIssueResult(Long userId, Long couponId) {
        return couponIssueResultRepository.findByUserIdAndCouponId(userId, couponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "발급 요청을 찾을 수 없습니다."));
    }

    @Transactional
    public void restoreUserCoupon(Long userCouponId) {
        UserCoupon userCoupon = userCouponRepository.findById(userCouponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "발급된 쿠폰을 찾을 수 없습니다."));
        userCoupon.restore();
    }
}
