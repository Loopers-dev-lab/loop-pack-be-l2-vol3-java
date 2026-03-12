package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.OwnedCouponModel;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OwnedCouponJpaRepository extends JpaRepository<OwnedCouponModel, Long> {

    Optional<OwnedCouponModel> findByCouponIdAndUserId(Long couponId, Long userId);

    List<OwnedCouponModel> findAllByUserId(Long userId);

    Page<OwnedCouponModel> findAllByCouponId(Long couponId, Pageable pageable);

    Optional<OwnedCouponModel> findByOrderId(Long orderId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OwnedCouponModel o"
            + " SET o.orderId = :orderId, o.usedAt = :usedAt"
            + " WHERE o.id = :id AND o.orderId IS NULL")
    int useByIdWhenAvailable(
            @Param("id") Long id,
            @Param("orderId") Long orderId,
            @Param("usedAt") ZonedDateTime usedAt);
}
