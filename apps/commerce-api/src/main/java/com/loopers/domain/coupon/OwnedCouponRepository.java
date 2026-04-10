package com.loopers.domain.coupon;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OwnedCouponRepository {

    OwnedCouponModel save(OwnedCouponModel ownedCouponModel);

    Optional<OwnedCouponModel> findById(Long id);

    Optional<OwnedCouponModel> findByCouponIdAndUserId(Long couponId, Long userId);

    List<OwnedCouponModel> findAllByUserId(Long userId);

    Page<OwnedCouponModel> findAllByCouponId(Long couponId, Pageable pageable);

    Optional<OwnedCouponModel> findByOrderId(Long orderId);

    int useByIdWhenAvailable(Long id, Long orderId, ZonedDateTime usedAt);

    long countByCouponId(Long couponId);

    Map<Long, Long> countByCouponIds(List<Long> couponIds);
}
