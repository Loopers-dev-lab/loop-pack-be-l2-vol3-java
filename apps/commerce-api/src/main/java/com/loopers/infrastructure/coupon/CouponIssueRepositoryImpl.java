package com.loopers.infrastructure.coupon;

import com.loopers.domain.PageResult;
import com.loopers.domain.coupon.CouponIssue;
import com.loopers.domain.coupon.CouponIssueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CouponIssueRepositoryImpl implements CouponIssueRepository {

    private final CouponIssueJpaRepository couponIssueJpaRepository;

    @Override
    public CouponIssue save(CouponIssue couponIssue) {
        return couponIssueJpaRepository.save(couponIssue);
    }

    @Override
    public Optional<CouponIssue> findById(Long id) {
        return couponIssueJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public boolean existsByCouponIdAndUserId(Long couponId, Long userId) {
        return couponIssueJpaRepository.existsByCouponIdAndUserIdAndDeletedAtIsNull(couponId, userId);
    }

    @Override
    public List<CouponIssue> findAllByUserId(Long userId) {
        return couponIssueJpaRepository.findAllByUserIdAndDeletedAtIsNull(userId);
    }

    @Override
    public PageResult<CouponIssue> findByCouponId(Long couponId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CouponIssue> result = couponIssueJpaRepository.findByCouponIdAndDeletedAtIsNull(couponId, pageRequest);
        return new PageResult<>(
            result.getContent(), result.getNumber(), result.getSize(),
            result.getTotalElements(), result.getTotalPages()
        );
    }
}
