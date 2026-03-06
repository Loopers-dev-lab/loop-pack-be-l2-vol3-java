package com.loopers.infrastructure.coupon;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CouponJpaRepository extends JpaRepository<CouponEntity, UUID> {
    List<CouponEntity> findAllByIdIn(List<UUID> ids);

    Page<CouponEntity> findByDeletedAtIsNull(Pageable pageable);

    Optional<CouponEntity> findByIdAndDeletedAtIsNull(UUID id);
}
