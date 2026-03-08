package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssue;
import com.loopers.domain.coupon.CouponIssueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CouponIssueRepositoryAdapter implements CouponIssueRepository {

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
    public Optional<CouponIssue> findByIdAndUserId(Long id, Long userId) {
        return couponIssueJpaRepository.findByIdAndUserId(id, userId);
    }

    @Override
    public Optional<CouponIssue> findByCouponIdAndUserId(Long couponId, Long userId) {
        return couponIssueJpaRepository.findByCouponIdAndUserId(couponId, userId);
    }

    @Override
    public Page<CouponIssue> findByCouponId(Long couponId, Pageable pageable) {
        return couponIssueJpaRepository.findAllByCouponId(couponId, pageable);
    }

    @Override
    public Page<CouponIssue> findByUserId(Long userId, Pageable pageable) {
        return couponIssueJpaRepository.findAllByUserId(userId, pageable);
    }
}
