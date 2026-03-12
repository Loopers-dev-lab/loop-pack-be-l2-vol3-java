package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponModel;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponJpaRepository extends JpaRepository<CouponModel, Long> {

    Optional<CouponModel> findByIdAndDeletedAtIsNull(Long id);

    Page<CouponModel> findAllByDeletedAtIsNull(Pageable pageable);
}
