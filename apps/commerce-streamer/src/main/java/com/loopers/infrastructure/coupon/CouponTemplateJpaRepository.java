package com.loopers.infrastructure.coupon;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponTemplateJpaRepository extends JpaRepository<CouponTemplateEntity, Long> {

    /**
     * 선착순 발급 수량 원자적 증가.
     * max_issuance_count가 null(무제한)이거나 issued_count < max_issuance_count인 경우에만 증가.
     * @return 업데이트된 row 수 (1이면 성공, 0이면 수량 초과)
     */
    @Modifying
    @Query(value = """
        UPDATE coupon_templates
        SET issued_count = issued_count + 1
        WHERE id = :templateId
          AND (max_issuance_count IS NULL OR issued_count < max_issuance_count)
        """, nativeQuery = true)
    int tryIncrementIssuedCount(@Param("templateId") Long templateId);
}
