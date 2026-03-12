package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueModel;
import com.loopers.domain.coupon.CouponIssueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CouponIssueRepositoryImpl implements CouponIssueRepository {

    private final CouponIssueJpaRepository couponIssueJpaRepository;

    @Override
    public CouponIssueModel save(CouponIssueModel couponIssue) {
        return couponIssueJpaRepository.save(couponIssue);
    }

    @Override
    public Optional<CouponIssueModel> findById(Long id) {
        return couponIssueJpaRepository.findById(id);
    }

    @Override
    public Optional<CouponIssueModel> findByIdForUpdate(Long id) {
        return couponIssueJpaRepository.findByIdForUpdate(id);
    }

    @Override
    public List<CouponIssueModel> findAllByUserId(Long userId) {
        return couponIssueJpaRepository.findAllByUserId(userId);
    }

    @Override
    public Page<CouponIssueModel> findAllByCouponId(Long couponId, Pageable pageable) {
        return couponIssueJpaRepository.findAllByCouponId(couponId, pageable);
    }

    @Override
    public Optional<CouponIssueModel> findByUserIdAndCouponId(Long userId, Long couponId) {
        return couponIssueJpaRepository.findByUserIdAndCouponId(userId, couponId);
    }
}
