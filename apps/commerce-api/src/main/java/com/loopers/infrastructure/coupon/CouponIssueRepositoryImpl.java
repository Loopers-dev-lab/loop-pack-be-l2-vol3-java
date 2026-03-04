package com.loopers.infrastructure.coupon;

import com.loopers.domain.PageResult;
import com.loopers.domain.coupon.CouponIssue;
import com.loopers.domain.coupon.CouponIssueRepository;
import com.loopers.infrastructure.support.ConstraintViolationHelper;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
        try {
            return couponIssueJpaRepository.saveAndFlush(couponIssue);
        } catch (DataIntegrityViolationException e) {
            if (ConstraintViolationHelper.isUniqueViolation(e, "uk_coupon_issues_coupon_user")) {
                throw new CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.");
            }
            throw e;
        }
    }

    @Override
    public Optional<CouponIssue> findById(Long id) {
        return couponIssueJpaRepository.findByIdAndDeletedAtIsNull(id);
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
