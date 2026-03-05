package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssue;
import com.loopers.domain.coupon.CouponIssueRepository;
import com.loopers.domain.coupon.CouponIssueStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CouponIssueRepositoryImpl implements CouponIssueRepository {

    private final CouponIssueJpaRepository couponIssueJpaRepository;

    @Override
    public CouponIssue save(CouponIssue couponIssue) {
        return couponIssueJpaRepository.save(couponIssue);
    }

    @Override
    public Optional<CouponIssue> findById(Long id) {
        return couponIssueJpaRepository.findById(id);
    }

    @Override
    public int markAsUsed(Long id, ZonedDateTime now) {
        return couponIssueJpaRepository.markAsUsed(
            id, now, CouponIssueStatus.USED, CouponIssueStatus.AVAILABLE);
    }

    @Override
    public List<CouponIssue> findAllByMemberId(Long memberId) {
        return couponIssueJpaRepository.findAllByMemberId(memberId);
    }

    @Override
    public List<CouponIssue> findAllByCouponId(Long couponId) {
        return couponIssueJpaRepository.findAllByCouponId(couponId);
    }
}
