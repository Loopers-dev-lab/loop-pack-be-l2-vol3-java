package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssue;
import com.loopers.domain.coupon.CouponIssueRepository;
import com.loopers.infrastructure.support.ConstraintViolationHelper;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class CouponIssueRepositoryImpl implements CouponIssueRepository {

    private final CouponIssueJpaRepository couponIssueJpaRepository;

    @Override
    public CouponIssue save(CouponIssue couponIssue) {
        try {
            return couponIssueJpaRepository.saveAndFlush(couponIssue);
        } catch (DataIntegrityViolationException e) {
            if (ConstraintViolationHelper.isUniqueViolation(e, "uk_coupon_issues_coupon_user")) {
                throw new CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.");
            }
            throw e;
        }
    }
}
