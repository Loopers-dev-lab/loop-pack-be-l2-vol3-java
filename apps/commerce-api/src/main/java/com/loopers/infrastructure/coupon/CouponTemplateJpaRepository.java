package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CouponTemplateJpaRepository extends JpaRepository<CouponTemplate, Long> {

    Page<CouponTemplate> findAllByDeletedAtIsNull(Pageable pageable);

    List<CouponTemplate> findAllByIdInAndDeletedAtIsNull(Collection<Long> ids);
}
