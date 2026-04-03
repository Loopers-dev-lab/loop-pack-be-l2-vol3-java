package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CouponRepositoryImpl implements CouponRepository {

    private final CouponJpaRepository couponJpaRepository;

    @Override
    public Coupon save(Coupon coupon) {
        if (coupon.id() != null) {
            Optional<CouponEntity> existing = couponJpaRepository.findByIdAndDeletedAtIsNull(coupon.id());
            if (existing.isPresent()) {
                CouponEntity entity = existing.get();
                entity.updateFrom(coupon);
                return couponJpaRepository.save(entity).toDomain();
            }
        }
        return couponJpaRepository.save(CouponEntity.from(coupon)).toDomain();
    }

    @Override
    public Optional<Coupon> findById(UUID id) {
        return couponJpaRepository.findByIdAndDeletedAtIsNull(id).map(CouponEntity::toDomain);
    }

    @Override
    public Page<Coupon> findAll(Pageable pageable) {
        return couponJpaRepository.findByDeletedAtIsNull(pageable).map(CouponEntity::toDomain);
    }

    @Override
    public List<Coupon> findAllByIdIn(List<UUID> ids) {
        return couponJpaRepository.findAllByIdIn(ids).stream().map(CouponEntity::toDomain).toList();
    }

    @Override
    public Map<UUID, Coupon> findAllMapByIdIn(List<UUID> ids) {
        return couponJpaRepository.findAllByIdIn(ids).stream()
                .collect(Collectors.toMap(CouponEntity::getId, CouponEntity::toDomain));
    }

    @Override
    public int decreaseRemainingQuantityAtomically(UUID couponId, int quantity) {
        return couponJpaRepository.decreaseRemainingQuantityAtomically(couponId, quantity);
    }

    @Override
    public int increaseRemainingQuantityAtomically(UUID couponId, int quantity) {
        return couponJpaRepository.increaseRemainingQuantityAtomically(couponId, quantity);
    }

    @Override
    public void delete(Coupon coupon) {
        Optional<CouponEntity> entity = couponJpaRepository.findByIdAndDeletedAtIsNull(coupon.id());
        if (entity.isPresent()) {
            CouponEntity found = entity.get();
            found.delete();
            couponJpaRepository.save(found);
        }
    }
}
