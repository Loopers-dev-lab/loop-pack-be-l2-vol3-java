package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class IssuedCouponRepositoryImpl implements IssuedCouponRepository {

    private final IssuedCouponJpaRepository issuedCouponJpaRepository;
    private final IssuedCouponMapper issuedCouponMapper;

    public IssuedCouponRepositoryImpl(IssuedCouponJpaRepository issuedCouponJpaRepository,
                                       IssuedCouponMapper issuedCouponMapper) {
        this.issuedCouponJpaRepository = issuedCouponJpaRepository;
        this.issuedCouponMapper = issuedCouponMapper;
    }

    @Override
    public IssuedCoupon save(IssuedCoupon issuedCoupon) {
        IssuedCouponEntity entity = issuedCouponMapper.toEntity(issuedCoupon);
        IssuedCouponEntity saved = issuedCouponJpaRepository.save(entity);
        return issuedCouponMapper.toDomain(saved);
    }

    @Override
    public Optional<IssuedCoupon> findById(Long id) {
        return issuedCouponJpaRepository.findById(id)
                .map(issuedCouponMapper::toDomain);
    }

    @Override
    public long countByCouponTemplateId(Long couponTemplateId) {
        return issuedCouponJpaRepository.countByCouponTemplateId(couponTemplateId);
    }

    @Override
    public long countByCouponTemplateIdAndUserId(Long couponTemplateId, Long userId) {
        return issuedCouponJpaRepository.countByCouponTemplateIdAndUserId(couponTemplateId, userId);
    }

    @Override
    public List<IssuedCoupon> findAllByUserId(Long userId) {
        return issuedCouponJpaRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(issuedCouponMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<IssuedCoupon> findAllByCouponTemplateId(Long couponTemplateId) {
        return issuedCouponJpaRepository.findAllByCouponTemplateIdOrderByCreatedAtDesc(couponTemplateId)
                .stream()
                .map(issuedCouponMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public int useAtomically(Long id, Long orderId, ZonedDateTime usedAt) {
        return issuedCouponJpaRepository.useAtomically(id, orderId, usedAt);
    }
}
