package com.loopers.infrastructure.coupon;

import static com.loopers.infrastructure.coupon.QCouponEntity.couponEntity;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CouponRepositoryImpl implements CouponRepository {

    private final CouponJpaRepository couponJpaRepository;
    private final JPAQueryFactory queryFactory;

    @Override
    public Coupon save(Coupon coupon) {
        CouponEntity entity = CouponEntity.create(coupon);
        return couponJpaRepository.save(entity).toDomain();
    }

    @Override
    public Coupon update(Coupon coupon) {
        CouponEntity entity = couponJpaRepository.findById(coupon.getId())
            .orElseThrow(() -> new IllegalArgumentException("Coupon not found: " + coupon.getId()));
        entity.markAsUsed();
        return entity.toDomain();
    }

    @Override
    public Optional<Coupon> findById(Long id) {
        return couponJpaRepository.findById(id).map(CouponEntity::toDomain);
    }

    @Override
    public List<Coupon> findByUserId(Long userId) {
        return queryFactory
            .selectFrom(couponEntity)
            .where(couponEntity.refUserId.eq(userId))
            .orderBy(couponEntity.createdAt.desc())
            .fetch()
            .stream()
            .map(CouponEntity::toDomain)
            .toList();
    }
}
