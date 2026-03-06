package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponTemplateProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponTemplateJpaRepository extends JpaRepository<CouponTemplateModel, Long> {

    Optional<CouponTemplateModel> findByIdAndDeletedAtIsNull(Long id);

    Page<CouponTemplateModel> findByDeletedAtIsNull(Pageable pageable);

    Page<CouponTemplateProjection> findProjectionByDeletedAtIsNull(Pageable pageable);

    Optional<CouponTemplateProjection> findProjectionByIdAndDeletedAtIsNull(Long id);
}
