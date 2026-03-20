package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class IssuedCouponRepositoryImpl implements IssuedCouponRepository {

    private final IssuedCouponJpaRepository issuedCouponJpaRepository;

    @Override
    public IssuedCoupon save(IssuedCoupon issuedCoupon) {
        Optional<IssuedCouponEntity> existing = issuedCouponJpaRepository.findByMemberIdAndCouponId(
                issuedCoupon.memberId(), issuedCoupon.couponId());

        if (existing.isPresent()) {
            IssuedCouponEntity entity = existing.get();
            entity.updateFrom(issuedCoupon);
            return issuedCouponJpaRepository.save(entity).toDomain();
        }

        return issuedCouponJpaRepository.save(IssuedCouponEntity.from(issuedCoupon)).toDomain();
    }

    @Override
    public Optional<IssuedCoupon> findByMemberIdAndCouponId(String memberId, UUID couponId) {
        return issuedCouponJpaRepository.findByMemberIdAndCouponId(memberId, couponId)
                .map(IssuedCouponEntity::toDomain);
    }

    @Override
    public int markUsedAtomically(String memberId, UUID couponId, LocalDateTime now) {
        return issuedCouponJpaRepository.markUsedAtomically(
                memberId,
                couponId,
                CouponStatus.AVAILABLE,
                CouponStatus.USED,
                now,
                now
        );
    }

    @Override
    public int markAvailableAtomically(String memberId, UUID couponId) {
        return issuedCouponJpaRepository.markAvailableAtomically(
                memberId,
                couponId,
                CouponStatus.AVAILABLE,
                CouponStatus.USED
        );
    }

    @Override
    public Page<IssuedCoupon> findByCouponId(UUID couponId, Pageable pageable) {
        return issuedCouponJpaRepository.findByCouponIdOrderByCreatedAtDesc(couponId, pageable)
                .map(IssuedCouponEntity::toDomain);
    }

    @Override
    public List<IssuedCoupon> findByMemberId(String memberId) {
        return issuedCouponJpaRepository.findByMemberIdOrderByCreatedAtDesc(memberId)
                .stream().map(IssuedCouponEntity::toDomain).toList();
    }
}
