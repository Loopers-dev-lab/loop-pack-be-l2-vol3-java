package com.loopers.infrastructure.persistence.jpa.coupon;

import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * IssuedCouponRepository 구현체.
 */
@Repository
@RequiredArgsConstructor
public class IssuedCouponRepositoryImpl implements IssuedCouponRepository {

    private final IssuedCouponJpaRepository jpaRepository;

    @Override
    public IssuedCoupon save(IssuedCoupon coupon) {
        IssuedCouponJpaEntity entity;

        if (coupon.getId() == null) {
            entity = IssuedCouponMapper.toJpaEntity(coupon);
        } else {
            entity = jpaRepository.findById(coupon.getId())
                .orElseGet(() -> IssuedCouponMapper.toJpaEntity(coupon));
            IssuedCouponMapper.updateJpaEntity(entity, coupon);
        }

        IssuedCouponJpaEntity saved = jpaRepository.save(entity);
        return IssuedCouponMapper.toDomain(saved);
    }

    @Override
    public Optional<IssuedCoupon> findById(Long id) {
        return jpaRepository.findById(id)
            .map(IssuedCouponMapper::toDomain);
    }

    @Override
    public Optional<IssuedCoupon> findByIdWithLock(Long id) {
        return jpaRepository.findByIdWithLock(id)
            .map(IssuedCouponMapper::toDomain);
    }

    @Override
    public Optional<IssuedCoupon> findByIdAndUserId(Long id, Long userId) {
        return jpaRepository.findByIdAndUserId(id, userId)
            .map(IssuedCouponMapper::toDomain);
    }

    @Override
    public List<IssuedCoupon> findAllByUserId(Long userId, int offset, int limit) {
        PageRequest pageable = PageRequest.of(offset / limit, limit, Sort.by(Sort.Direction.DESC, "issuedAt"));
        return jpaRepository.findAllByUserId(userId, pageable).stream()
            .map(IssuedCouponMapper::toDomain)
            .toList();
    }

    @Override
    public boolean existsByUserIdAndCouponTemplateId(Long userId, Long couponTemplateId) {
        return jpaRepository.existsByUserIdAndCouponTemplateId(userId, couponTemplateId);
    }

    @Override
    public long countByUserId(Long userId) {
        return jpaRepository.countByUserId(userId);
    }

    @Override
    public List<IssuedCoupon> findAllByCouponTemplateId(Long couponTemplateId, int offset, int limit) {
        PageRequest pageable = PageRequest.of(offset / limit, limit, Sort.by(Sort.Direction.DESC, "issuedAt"));
        return jpaRepository.findAllByCouponTemplateId(couponTemplateId, pageable).stream()
            .map(IssuedCouponMapper::toDomain)
            .toList();
    }

    @Override
    public long countByCouponTemplateId(Long couponTemplateId) {
        return jpaRepository.countByCouponTemplateId(couponTemplateId);
    }
}
