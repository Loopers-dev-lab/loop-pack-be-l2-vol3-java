package com.loopers.domain.coupon;

import com.loopers.domain.PageResult;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class CouponIssueDomainService {

    private final CouponIssueRepository couponIssueRepository;

    public CouponIssue issue(Coupon coupon, Long userId) {
        if (coupon.getDeletedAt() != null) {
            throw new CoreException(ErrorType.NOT_FOUND, "삭제된 쿠폰은 발급할 수 없습니다.");
        }
        if (coupon.isExpired()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰은 발급할 수 없습니다.");
        }
        return couponIssueRepository.save(new CouponIssue(coupon.getId(), userId));
    }

    public CouponIssue getByIdAndUserId(Long id, Long userId) {
        CouponIssue issue = couponIssueRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 발급 내역을 찾을 수 없습니다."));
        if (!issue.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "쿠폰 발급 내역을 찾을 수 없습니다.");
        }
        return issue;
    }

    public List<CouponIssue> getMyIssues(Long userId) {
        return couponIssueRepository.findAllByUserId(userId);
    }

    public PageResult<CouponIssue> getIssuesByCouponId(Long couponId, int page, int size) {
        return couponIssueRepository.findByCouponId(couponId, page, size);
    }

    public CouponIssue useCoupon(Long couponIssueId, Long userId) {
        CouponIssue issue = getByIdAndUserId(couponIssueId, userId);
        issue.use();
        return couponIssueRepository.save(issue);
    }

    public void restoreCoupon(Long couponIssueId) {
        CouponIssue issue = couponIssueRepository.findById(couponIssueId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 발급 내역을 찾을 수 없습니다."));
        issue.restore();
        couponIssueRepository.save(issue);
    }
}
