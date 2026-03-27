package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponTemplateProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponTemplateJpaRepository extends JpaRepository<CouponTemplateModel, Long> {

    Optional<CouponTemplateModel> findByIdAndDeletedAtIsNull(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CouponTemplateModel c WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<CouponTemplateModel> findByIdAndDeletedAtIsNullForUpdate(@Param("id") Long id);

    Page<CouponTemplateModel> findByDeletedAtIsNull(Pageable pageable);

    Page<CouponTemplateProjection> findProjectionByDeletedAtIsNull(Pageable pageable);

    Optional<CouponTemplateProjection> findProjectionByIdAndDeletedAtIsNull(Long id);
}
