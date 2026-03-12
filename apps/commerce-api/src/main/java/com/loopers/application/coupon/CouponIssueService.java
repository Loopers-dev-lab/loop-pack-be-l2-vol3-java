package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueModel;
import com.loopers.domain.coupon.CouponIssueRepository;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.dao.DataIntegrityViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class CouponIssueService {

    private final CouponIssueRepository couponIssueRepository;

    @Transactional
    public CouponIssueModel issue(CouponModel coupon, Long userId) {
        if (coupon.isExpired()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰은 발급할 수 없습니다.");
        }

        couponIssueRepository.findByUserIdAndCouponId(userId, coupon.getId())
            .ifPresent(existing -> {
                throw new CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.");
            });

        CouponIssueModel couponIssue = new CouponIssueModel(coupon.getId(), userId, coupon.expiredAt());
        try {
            return couponIssueRepository.save(couponIssue);
        } catch (DataIntegrityViolationException e) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.");
        }
    }

    @Transactional(readOnly = true)
    public CouponIssueModel getCouponIssue(Long couponIssueId) {
        return couponIssueRepository.findById(couponIssueId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "발급된 쿠폰을 찾을 수 없습니다."));
    }

    public CouponIssueModel getCouponIssueForUpdate(Long couponIssueId) {
        return couponIssueRepository.findByIdForUpdate(couponIssueId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "발급된 쿠폰을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<CouponIssueModel> getMyIssues(Long userId) {
        return couponIssueRepository.findAllByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Page<CouponIssueModel> getIssuesByCoupon(Long couponId, Pageable pageable) {
        return couponIssueRepository.findAllByCouponId(couponId, pageable);
    }

    @Transactional
    public void use(Long couponIssueId, Long userId, Long orderId) {
        CouponIssueModel couponIssue = getCouponIssue(couponIssueId);
        couponIssue.validateOwner(userId);
        couponIssue.use(orderId);
    }
}
