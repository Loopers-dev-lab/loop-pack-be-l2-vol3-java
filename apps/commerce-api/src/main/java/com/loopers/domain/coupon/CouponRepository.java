package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface CouponRepository {
    Coupon save(Coupon coupon);

    Optional<Coupon> findById(UUID id);

    Page<Coupon> findAll(Pageable pageable);

    List<Coupon> findAllByIdIn(List<UUID> ids);

    Map<UUID, Coupon> findAllMapByIdIn(List<UUID> ids);

    int decreaseRemainingQuantityAtomically(UUID couponId, int quantity);

    int increaseRemainingQuantityAtomically(UUID couponId, int quantity);

    void delete(Coupon coupon);
}
