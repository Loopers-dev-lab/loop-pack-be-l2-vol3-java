package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.support.page.PageQuery;
import com.loopers.support.page.PagedResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 쿠폰 레포지토리 구현체.
 */
@Repository
@RequiredArgsConstructor
public class CouponRepositoryImpl implements CouponRepository {

    private final CouponJpaRepository couponJpaRepository;

    @Override
    public Optional<CouponModel> findById(Long couponId) {
        return couponJpaRepository.findById(couponId);
    }

    @Override
    public Optional<CouponModel> findByIdWithLock(Long couponId) {
        return couponJpaRepository.findByIdWithLock(couponId);
    }

    @Override
    public CouponModel save(CouponModel coupon) {
        return couponJpaRepository.save(coupon);
    }

    @Override
    public List<CouponModel> findAll() {
        return couponJpaRepository.findAll();
    }

    @Override
    public List<CouponModel> findAllByIdIn(Collection<Long> ids) {
        return couponJpaRepository.findAllByCouponIdIn(ids);
    }

    @Override
    public PagedResult<CouponModel> findAllPaged(PageQuery query) {
        Sort sort = query.ascending()
                ? Sort.by(Sort.Direction.ASC, query.sortField())
                : Sort.by(Sort.Direction.DESC, query.sortField());
        Page<CouponModel> page = couponJpaRepository.findAll(
                PageRequest.of(query.page(), query.size(), sort));
        return new PagedResult<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
