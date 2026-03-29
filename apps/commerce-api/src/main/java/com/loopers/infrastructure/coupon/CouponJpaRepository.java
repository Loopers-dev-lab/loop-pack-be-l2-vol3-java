package com.loopers.infrastructure.coupon;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CouponJpaRepository extends JpaRepository<CouponEntity, UUID> {
    List<CouponEntity> findAllByIdIn(List<UUID> ids);

    Page<CouponEntity> findByDeletedAtIsNull(Pageable pageable);

    Optional<CouponEntity> findByIdAndDeletedAtIsNull(UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CouponEntity c
            set c.remainingQuantity = c.remainingQuantity - :quantity
            where c.id = :couponId
              and c.deletedAt is null
              and c.remainingQuantity >= :quantity
            """)
    int decreaseRemainingQuantityAtomically(@Param("couponId") UUID couponId, @Param("quantity") int quantity);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CouponEntity c
            set c.remainingQuantity = case
                when c.remainingQuantity + :quantity > c.totalQuantity then c.totalQuantity
                else c.remainingQuantity + :quantity
            end
            where c.id = :couponId
              and c.deletedAt is null
            """)
    int increaseRemainingQuantityAtomically(@Param("couponId") UUID couponId, @Param("quantity") int quantity);
}
